import React, { useState, useCallback, useEffect } from "react";
import { cloneDeep, debounce, isEmpty, sortBy } from "lodash";

import BaseControl, { ControlProps } from "./BaseControl";
import EmptyDataState from "components/utils/EmptyDataState";
import SchemaParser from "widgets/FormBuilderWidget/schemaParser";
import styled from "constants/DefaultTheme";
import { ARRAY_ITEM_KEY, Schema } from "widgets/FormBuilderWidget/constants";
import { Category, Size } from "components/ads/Button";
import {
  BaseItemProps,
  DroppableComponent,
  RenderComponentProps,
} from "components/ads/DraggableListComponent";
import {
  StyledDeleteIcon,
  StyledDragIcon,
  StyledEditIcon,
  StyledHiddenIcon,
  StyledInputGroup,
  StyledPropertyPaneButton,
  StyledVisibleIcon,
} from "./StyledControls";
import { getNextEntityName } from "utils/AppsmithUtils";
import { InputText } from "./InputTextControl";

type DroppableItem = BaseItemProps & {
  index: number;
  isCustomField: boolean;
};

const TabsWrapper = styled.div`
  width: 100%;
  display: flex;
  flex-direction: column;
`;

const ItemWrapper = styled.div`
  display: flex;
  justify-content: flex-start;
  align-items: center;
`;

const StyledOptionControlInputGroup = styled(StyledInputGroup)`
  margin-right: 2px;
  margin-bottom: 2px;
  width: 100%;
  padding-left: 10px;
  padding-right: 60px;
  text-overflow: ellipsis;
  background: inherit;
  &&& {
    input {
      padding-left: 24px;
      border: none;
      color: ${(props) => props.theme.colors.textOnDarkBG};
      &:focus {
        border: none;
        color: ${(props) => props.theme.colors.textOnDarkBG};
      }
    }
  }
`;

const AddColumnButton = styled(StyledPropertyPaneButton)`
  width: 100%;
  display: flex;
  justify-content: center;
  &&&& {
    margin-top: 12px;
    margin-bottom: 8px;
  }
`;

function DroppableRenderComponent(props: RenderComponentProps<DroppableItem>) {
  const {
    deleteOption,
    index,
    item,
    onEdit,
    toggleVisibility,
    updateOption,
  } = props;
  const { id, isCustomField, isVisible, label = "" } = item;
  const [value, setValue] = useState(label);
  const [isEditing, setEditing] = useState(false);

  useEffect(() => {
    if (!isEditing && label) {
      setValue(label);
    }
  }, [label]);

  const onFocus = () => setEditing(true);
  const onBlur = () => setEditing(false);

  const debouncedUpdate = debounce(updateOption, 1000);

  const onLabelChange = useCallback(
    (index: number, value: string) => {
      setValue(value);
      debouncedUpdate(index, value);
    },
    [updateOption],
  );

  const deleteIcon = (() => {
    if (!isCustomField || id === ARRAY_ITEM_KEY) return null;

    return (
      <StyledDeleteIcon
        className="t--delete-column-btn"
        height={20}
        onClick={() => {
          deleteOption?.(index);
        }}
        width={20}
      />
    );
  })();

  const hideShowIcon = (() => {
    if (isCustomField || id === ARRAY_ITEM_KEY) return null;

    return isVisible ? (
      <StyledVisibleIcon
        className="t--show-column-btn"
        height={20}
        onClick={() => {
          toggleVisibility?.(index);
        }}
        width={20}
      />
    ) : (
      <StyledHiddenIcon
        className="t--show-column-btn"
        height={20}
        onClick={() => {
          toggleVisibility?.(index);
        }}
        width={20}
      />
    );
  })();

  return (
    <ItemWrapper>
      <StyledDragIcon height={20} width={20} />
      <StyledOptionControlInputGroup
        dataType="text"
        onBlur={onBlur}
        onChange={(value: string) => {
          onLabelChange(index, value);
        }}
        onFocus={onFocus}
        placeholder="Column Title"
        value={value}
      />
      <StyledEditIcon
        className="t--edit-column-btn"
        height={20}
        onClick={() => onEdit?.(index)}
        width={20}
      />
      {deleteIcon}
      {hideShowIcon}
    </ItemWrapper>
  );
}

class FieldConfigurationControl extends BaseControl<ControlProps> {
  isArrayItem = () => {
    const schema: Schema = this.props.propertyValue;
    return Boolean(schema?.[ARRAY_ITEM_KEY]);
  };

  findSchemaItem = (index: number) => {
    const schema: Schema = this.props.propertyValue;
    const schemaItems = Object.values(schema);
    const sortedSchemaItems = sortBy(schemaItems, ({ position }) => position);
    return sortedSchemaItems[index];
  };

  onEdit = (index: number) => {
    const schemaItem = this.findSchemaItem(index);

    if (schemaItem) {
      this.props.openNextPanel({
        ...schemaItem,
        propPaneId: this.props.widgetProperties.widgetId,
      });
    }
  };

  onDeleteOption = (index: number) => {
    const { propertyName } = this.props;

    const schemaItem = this.findSchemaItem(index);

    if (schemaItem) {
      const itemToDeletePath = `${propertyName}.${schemaItem.name}`;

      this.deleteProperties([itemToDeletePath]);
    }
  };

  updateOption = (index: number, updatedLabel: string) => {
    const { propertyName } = this.props;
    const schemaItem = this.findSchemaItem(index);

    if (schemaItem) {
      const { name } = schemaItem;

      this.updateProperty(`${propertyName}.${name}.label`, updatedLabel);
    }
  };

  toggleVisibility = (index: number) => {
    const { propertyName } = this.props;
    const schemaItem = this.findSchemaItem(index);

    if (schemaItem) {
      const { isVisible, name } = schemaItem;

      this.updateProperty(`${propertyName}.${name}.isVisible`, !isVisible);
    }
  };

  addNewColumn = () => {
    if (this.isArrayItem()) return;

    const { propertyValue = {}, propertyName, widgetProperties } = this.props;
    const { widgetName } = widgetProperties;
    const schema: Schema = propertyValue;
    const existingKeys = Object.keys(schema);
    const nextFieldKey = getNextEntityName("customField", existingKeys);
    const schemaItem = SchemaParser.getSchemaItemFor(nextFieldKey, {
      currSourceData: "",
      widgetName,
    });

    schemaItem.isCustomField = true;
    schemaItem.position = existingKeys.length;

    this.updateProperty(`${propertyName}.${nextFieldKey}`, schemaItem);
  };

  onInputChange = (event: React.ChangeEvent<HTMLTextAreaElement> | string) => {
    let value = event;
    if (typeof event !== "string") {
      value = event.target.value;
    }

    try {
      const parsedValue = JSON.parse(value as string);
      this.updateProperty(this.props.propertyName, parsedValue);
    } catch {
      // TODO: Try to throw some error
    }
  };

  updateItems = (items: DroppableItem[]) => {
    const { propertyName, propertyValue } = this.props;
    const clonedSchema: Schema = cloneDeep(propertyValue);

    items.forEach((item, index) => {
      clonedSchema[item.id].position = index;
    });

    this.updateProperty(propertyName, clonedSchema);
  };

  render() {
    const { propertyValue = {}, panelConfig } = this.props;
    const schema: Schema = propertyValue;
    const schemaItems = Object.values(schema);

    if (isEmpty(schema)) {
      return <EmptyDataState />;
    }

    const sortedSchemaItems = sortBy(schemaItems, ({ position }) => position);

    const isMaxLevelReached = Boolean(!panelConfig);

    const draggableComponentColumns: DroppableItem[] = sortedSchemaItems.map(
      ({ isCustomField, isVisible, label, name }, index) => ({
        id: name,
        index,
        isCustomField,
        isVisible,
        label,
      }),
    );

    if (isMaxLevelReached) {
      const {
        additionalAutoComplete,
        dataTreePath,
        expected,
        hideEvaluatedValue,
        label,
        theme,
      } = this.props;
      const value = JSON.stringify(schema, null, 2);

      return (
        <InputText
          additionalAutocomplete={additionalAutoComplete}
          dataTreePath={dataTreePath}
          expected={expected}
          hideEvaluatedValue={hideEvaluatedValue}
          label={label}
          onChange={this.onInputChange}
          placeholder="{ name: 'John', dataType: 'string' }"
          theme={theme}
          value={value || "{}"}
        />
      );
    }

    return (
      <TabsWrapper>
        <DroppableComponent
          deleteOption={this.onDeleteOption}
          itemHeight={45}
          items={draggableComponentColumns}
          onEdit={this.onEdit}
          renderComponent={DroppableRenderComponent}
          toggleVisibility={this.toggleVisibility}
          updateItems={this.updateItems}
          updateOption={this.updateOption}
        />
        {!this.isArrayItem() && (
          <AddColumnButton
            category={Category.tertiary}
            className="t--add-column-btn"
            icon="plus"
            onClick={this.addNewColumn}
            size={Size.medium}
            tag="button"
            text="Add a new field"
            type="button"
          />
        )}
      </TabsWrapper>
    );
  }

  static getControlType() {
    return "FIELD_CONFIGURATION";
  }
}

export default FieldConfigurationControl;