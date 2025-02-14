package com.tapdata.tm.commons.dag.process;

import com.tapdata.tm.commons.dag.DAG;
import com.tapdata.tm.commons.dag.EqField;
import com.tapdata.tm.commons.dag.NodeType;
import com.tapdata.tm.commons.schema.Field;
import com.tapdata.tm.commons.schema.Schema;
import com.tapdata.tm.commons.schema.TableIndexColumn;
import com.tapdata.tm.commons.util.PartitionTableFieldRenameOperator;
import io.tapdata.entity.event.ddl.TapDDLEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * @author lg<lirufei0808 @ gmail.com>
 * @date 2021/11/5 上午10:22
 * @description
 */
@NodeType("field_rename_processor")
@Getter
@Setter
@Slf4j
public class FieldRenameProcessorNode extends FieldProcessorNode {

    @EqField
    private String fieldsNameTransform = "";

    public FieldRenameProcessorNode() {
        super("field_rename_processor");
    }

    @Override
    public Schema mergeSchema(List<Schema> inputSchemas, Schema schema, DAG.Options options) {
        Schema outputSchema = superMergeSchema(inputSchemas, schema);

        //operation里面操作了的数据 就不做统一大小写前后缀处理了
        Set<String> opFields;
        if (CollectionUtils.isNotEmpty(operations)) {
            opFields = operations.stream().map(Operation::getField).collect(Collectors.toSet());
        } else {
            opFields = new HashSet<>();
        }
        List<String> inputFields = inputSchemas.stream().map(Schema::getFields).flatMap(Collection::stream).map(Field::getFieldName).filter(f -> !opFields.contains(f)).collect(Collectors.toList());
        if (operations != null && operations.size() > 0) {
            PartitionTableFieldRenameOperator operator = new PartitionTableFieldRenameOperator();
            operations.forEach(operation -> {
                if (operation == null) {
                    return;
                }
//                if (operation.getId() == null) {
//                    log.warn("Invalid operation in node {}, id can not be empty.", getId());
//                    return;
//                }

                String operand = operation.getOp();
                String fieldName = operation.getField();
                if (StringUtils.isBlank(operand) || "RENAME".equalsIgnoreCase(operand)) {
                    for (Field field : outputSchema.getFields()) {
                        if (fieldName.equals(field.getFieldName())) {
                            operator.rename(fieldName, operation.getOperand());
                            field.setFieldName(operation.getOperand());
                            break;
                            //field.setOriginalFieldName(operation.getOperand());
                        }
                    }

                    Optional.ofNullable(outputSchema.getIndices()).ifPresent(indexList ->
                            indexList.forEach(index -> {
                                List<String> collect = index.getColumns().stream().map(TableIndexColumn::getColumnName)
                                        .collect(Collectors.toList());
                                if (collect.contains(operation.getField())) {
                                    index.getColumns().forEach(column -> {
                                        if (column.getColumnName().equals(operation.getField())) {
                                            column.setColumnName(operation.getOperand());
                                        }
                                    });
                                }
                            }));
                }

            });
            operator.endOf(outputSchema);
        }

        fieldNameReduction(inputFields, outputSchema.getFields(), fieldsNameTransform);
        fieldNameUpLow(inputFields, outputSchema.getFields(), fieldsNameTransform);

        return outputSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof FieldRenameProcessorNode) {
            Class className = FieldRenameProcessorNode.class;
            for (; className != Object.class; className = className.getSuperclass()) {
                java.lang.reflect.Field[] declaredFields = className.getDeclaredFields();
                for (java.lang.reflect.Field declaredField : declaredFields) {
                    EqField annotation = declaredField.getAnnotation(EqField.class);
                    if (annotation != null) {
                        try {
                            Object f2 = declaredField.get(o);
                            Object f1 = declaredField.get(this);
                            boolean b = fieldEq(f1, f2);
                            if (!b) {
                                return false;
                            }
                        } catch (IllegalAccessException e) {
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void fieldDdlEvent(TapDDLEvent event) throws Exception {
        for (Operation operation : operations) {
            if (operation.getOp().equals("RENAME")) {
                operation.matchPdkFieldEvent(event);
            }
        }
    }
}
