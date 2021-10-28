package com.external.plugins;

import com.appsmith.external.exceptions.pluginExceptions.AppsmithPluginError;
import com.appsmith.external.exceptions.pluginExceptions.AppsmithPluginException;
import com.appsmith.external.models.ActionConfiguration;
import com.appsmith.external.models.ActionExecutionResult;
import com.appsmith.external.models.DBAuth;
import com.appsmith.external.models.DatasourceConfiguration;
import com.appsmith.external.models.DatasourceTestResult;
import com.appsmith.external.models.Endpoint;
import com.appsmith.external.plugins.BasePlugin;
import com.appsmith.external.plugins.PluginExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.pf4j.Extension;
import org.pf4j.PluginWrapper;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.mail.AuthenticationFailedException;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.appsmith.external.helpers.PluginUtils.getValueSafelyFromFormData;

public class SmtpPlugin extends BasePlugin {
    public SmtpPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class SmtpPluginExecutor implements PluginExecutor<Session> {


        @Override
        public Mono<ActionExecutionResult> execute(Session connection, DatasourceConfiguration datasourceConfiguration, ActionConfiguration actionConfiguration) {

            Message message = new MimeMessage(connection);
            ActionExecutionResult result = new ActionExecutionResult();
            try {
                String fromAddress = (String) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.from");
                String toAddress = (String) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.to");
                String ccAddress = (String) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.cc");
                String bccAddress = (String) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.bcc");
                String subject = (String) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.subject");
                Boolean isReplyTo = (Boolean) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.isReplyTo");
                String replyTo = (isReplyTo != null && isReplyTo) ?
                        (String) getValueSafelyFromFormData(actionConfiguration.getFormData(), "send.replyTo") : null;

                if (!StringUtils.hasText(toAddress)) {
                    return Mono.error(new AppsmithPluginException(AppsmithPluginError.PLUGIN_EXECUTE_ARGUMENT_ERROR,
                            "Couldn't find a valid recipient address. Please check your action configuration."));
                }
                if (!StringUtils.hasText(fromAddress)) {
                    return Mono.error(new AppsmithPluginException(AppsmithPluginError.PLUGIN_EXECUTE_ARGUMENT_ERROR,
                            "Couldn't find a valid sender address. Please check your action configuration."));
                }
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
                message.setFrom(new InternetAddress(fromAddress));

                if (StringUtils.hasText(ccAddress)) {
                    message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccAddress));
                }
                if (StringUtils.hasText(bccAddress)) {
                    message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccAddress));
                }
                if (StringUtils.hasText(replyTo)) {
                    message.setReplyTo(InternetAddress.parse(replyTo));
                }

                message.setSubject(subject);

                String msg = StringUtils.hasText(actionConfiguration.getBody()) ? actionConfiguration.getBody() : "";

                MimeBodyPart mimeBodyPart = new MimeBodyPart();
                mimeBodyPart.setContent(msg, "text/html");

                Multipart multipart = new MimeMultipart();
                multipart.addBodyPart(mimeBodyPart);
                message.setContent(multipart);

                System.out.println("Going to send the email");
                Transport.send(message);
                System.out.println("Sent the email successfully");

                result.setIsExecutionSuccess(true);
                Map<String, String> responseBody = new HashMap<>();
                responseBody.put("message", "Sent the email successfully");

                result.setBody(objectMapper.writeValueAsString(responseBody));
            } catch (AddressException e) {
                e.printStackTrace();
                return Mono.error(new AppsmithPluginException(AppsmithPluginError.PLUGIN_ERROR,
                        "Unable to " + e.getMessage()));
            } catch (MessagingException e) {
                return Mono.error(new AppsmithPluginException(AppsmithPluginError.PLUGIN_ERROR,
                        "Unable to send email because of error: " + e.getMessage()));
            } catch (JsonProcessingException e) {
                return Mono.error(new AppsmithPluginException(AppsmithPluginError.PLUGIN_ERROR,
                        "Unable to send response for email plugin because of error: " + e.getMessage()));
            }

            return Mono.just(result);
        }

        @Override
        public Mono<Session> datasourceCreate(DatasourceConfiguration datasourceConfiguration) {

            Endpoint endpoint = datasourceConfiguration.getEndpoints().get(0);
            DBAuth authentication = (DBAuth) datasourceConfiguration.getAuthentication();

            Properties prop = new Properties();
            prop.put("mail.smtp.auth", true);
            prop.put("mail.smtp.starttls.enable", "true");
            prop.put("mail.smtp.host", endpoint.getHost());
            prop.put("mail.smtp.port", String.valueOf(endpoint.getPort()));
            prop.put("mail.smtp.ssl.trust", endpoint.getHost());

            String username = authentication.getUsername();
            String password = authentication.getPassword();

            Session session = Session.getInstance(prop, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            return Mono.just(session);
        }

        @Override
        public void datasourceDestroy(Session connection) {
            System.out.println("Going to destroy an email datasource");
            try {
                Transport transport = connection.getTransport();
                if (transport != null) {
                    transport.close();
                }
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public Set<String> validateDatasource(DatasourceConfiguration datasourceConfiguration) {
            System.out.println("Going to validate an email datasource");
            Set<String> invalids = new HashSet<>();
            if (datasourceConfiguration.getEndpoints() == null ||
                    datasourceConfiguration.getEndpoints().isEmpty() ||
                    datasourceConfiguration.getEndpoints().get(0) == null
            ) {
                invalids.add(new AppsmithPluginException(AppsmithPluginError.PLUGIN_DATASOURCE_ARGUMENT_ERROR,
                        "Could not find host address. Please edit the 'Hostname' field to provide the desired endpoint.").getMessage());
            } else {
                Endpoint endpoint = datasourceConfiguration.getEndpoints().get(0);
                if (!StringUtils.hasText(endpoint.getHost())) {
                    invalids.add(new AppsmithPluginException(AppsmithPluginError.PLUGIN_DATASOURCE_ARGUMENT_ERROR,
                            "Could not find host address. Please edit the 'Hostname' field to provide the desired endpoint.").getMessage());
                }
                if (endpoint.getPort() == null) {
                    invalids.add(new AppsmithPluginException(AppsmithPluginError.PLUGIN_DATASOURCE_ARGUMENT_ERROR,
                            "Could not find port. Please edit the 'Port' field to provide the desired endpoint.").getMessage());
                }
            }

            DBAuth authentication = (DBAuth) datasourceConfiguration.getAuthentication();
            if (authentication == null || !StringUtils.hasText(authentication.getUsername()) ||
                    !StringUtils.hasText(authentication.getPassword())
            ) {
                invalids.add(new AppsmithPluginException(AppsmithPluginError.PLUGIN_AUTHENTICATION_ERROR).getMessage());
            }

            return invalids;
        }

        @Override
        public Mono<DatasourceTestResult> testDatasource(DatasourceConfiguration datasourceConfiguration) {
            System.out.println("Going to test an email datasource");
            Mono<Session> sessionMono = datasourceCreate(datasourceConfiguration);
            return sessionMono.map(session -> {
                Set<String> invalids = new HashSet<>();
                try {
                    Transport transport = session.getTransport();
                    if (transport != null) {
                        transport.connect();
                    }
                    return invalids;
                } catch (NoSuchProviderException e) {
                    invalids.add("Unable to create underlying SMTP protocol. Please contact support");
                } catch (AuthenticationFailedException e) {
                    invalids.add("Authentication failed with the SMTP server. Please check your username/password settings.");
                } catch (MessagingException e) {
                    invalids.add("Unable to connect to SMTP server. Please check your host/port settings.");
                    e.printStackTrace();
                }
                return invalids;
            }).map(invalids -> {
                DatasourceTestResult testResult = new DatasourceTestResult(invalids);
                return testResult;
            });
        }

    }
}