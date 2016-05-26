/**
 * 
 */
package net.paoding.rose.jade.plugin.sql.dialect.standard;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.paoding.rose.jade.plugin.sql.Plum.Operator;
import net.paoding.rose.jade.plugin.sql.dialect.ISQLGenerator;
import net.paoding.rose.jade.plugin.sql.mapper.ConditionalOperationMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IColumnMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IOperationMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IParameterMapper;
import net.paoding.rose.jade.plugin.sql.util.PlumUtils;
import net.paoding.rose.jade.statement.StatementRuntime;

/**
 * @author Alan.Geng[gengzhi718@gmail.com]
 *
 */
public abstract class ConditionalGenerator implements ISQLGenerator {

	private static final Map<Operator, String> OPERATORS;
	
	private boolean containsConditions = false;
	
	static {
		Map<Operator, String> operators = new HashMap<Operator, String>(Operator.values().length);
		operators.put(Operator.EQ, " = ");
		operators.put(Operator.NE, " != ");
		operators.put(Operator.GE, " >= ");
		operators.put(Operator.GT, " > ");
		operators.put(Operator.LE, " <= ");
		operators.put(Operator.LT, " < ");
		operators.put(Operator.LIKE, " LIKE ");
		operators.put(Operator.IN, " IN ");
		
		OPERATORS = Collections.unmodifiableMap(operators);
	}
	
	@Override
	public String generate(IOperationMapper operationMapper, StatementRuntime runtime) {
		StringBuilder sql = new StringBuilder();
		sql = beforeApplyConditions((ConditionalOperationMapper) operationMapper, runtime, sql);
		sql = applyConditions((ConditionalOperationMapper) operationMapper, runtime, sql);
		sql = afterApplyConditions((ConditionalOperationMapper) operationMapper, runtime, sql);
		return sql.toString();
	}
	
	protected StringBuilder applyConditions(ConditionalOperationMapper operationMapper, StatementRuntime runtime, StringBuilder sql) {
		if(operationMapper.isPrimaryKeyMode()) {
			sql.append(" WHERE ");
			List<IColumnMapper> primaryKey = operationMapper.getTargetEntityMapper().getPrimaryKey();
			
			for(int i = 0; i < primaryKey.size(); i++) {
				IColumnMapper col = primaryKey.get(i);
				if(i > 0) {
					sql.append(" AND ");
				}
				
				sql.append(col.getDestName());
				sql.append(" = ");
				sql.append(":");
				
				// 复合主键一定要在DAO方法中的指明各个参数名，并与Bean的字段名(field.getName)一致
				if (primaryKey.size() > 1) {
				    sql.append(col.getOriginal().getName());
				} else {
				    sql.append(i + 1);
				}
			}
			
			containsConditions = true;
		} else if(operationMapper.isComplexMode()) {
			List<IParameterMapper> parameters = operationMapper.getParameters();
			if(PlumUtils.isNotEmpty(parameters)) {
				int i = operationMapper.getWhereAt();
				if(i < 0) {
					// 无任何参数被标记为Where，则视为所有参数都是Where条件。
					i = 0;
				}
				
				String and = "";
				for(; i < parameters.size(); i++) {
					IParameterMapper param = parameters.get(i);
					String condition = generateCondition(operationMapper, param, runtime, i);
					
					if(condition != null) {
						if(and.length() == 0) {
							sql.append(" WHERE ");
							containsConditions = true;
						}
						sql.append(and);
						sql.append(condition);
						and = " AND ";
					}
				}
			}
		} else {
			throw new UnsupportedOperationException("Unknown condition mode.");
		}
		return sql;
	}
	
	protected String generateCondition(ConditionalOperationMapper operationMapper, IParameterMapper param, StatementRuntime runtime, int index) {
		Operator op = param.getOperator();
		
		if(!OPERATORS.containsKey(op)) {
			return null;
		}
		
		Object value = runtime.getParameters().get(":" + (index + 1));
		boolean nullValue = value == null;
		boolean ignoreNull = param.isIgnoreNull();
		
		if((ignoreNull
				&& nullValue)
				|| (nullValue
				&& op == Operator.IN)) {
			// When parameter value is null and operator is "in", or ignore null value.
			return null;
		}
		
		StringBuilder sql = new StringBuilder();
		
		sql.append(param.getDestName());
		
		if(op != Operator.LIKE
				&& op != Operator.EQ
				&& op != Operator.IN) {
			// Multiple parameter value at the same column.(e.g. age >= 15 AND age < 22)
			// In "Jade" framework, Parameter value appears first will be overwritten when the same name in annotation "SQLParam".
			sql.append(OPERATORS.get(op));
			sql.append(":");
			sql.append(index + 1);
		} else {
			if(nullValue
					&& op == Operator.EQ) {
				sql.append(" is null ");
			} else {
				// Normally, the "like", "in" or "=" condition only once at the same column.
				sql.append(OPERATORS.get(op));
				if(op == Operator.IN) {
					sql.append("(");
				}
				sql.append(":");
				sql.append(param.getOriginalName());
				if(op == Operator.IN) {
					sql.append(")");
				}
			}
		}
		
		return sql.toString();
	}
	
	protected StringBuilder beforeApplyConditions(ConditionalOperationMapper operationMapper, StatementRuntime runtime, StringBuilder sql) {
		return sql;
	}
	
	protected StringBuilder afterApplyConditions(ConditionalOperationMapper operationMapper, StatementRuntime runtime, StringBuilder sql) {
		return sql;
	}
	
	protected boolean containsConditions() {
		return containsConditions;
	}

}
