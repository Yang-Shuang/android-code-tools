<#if !isInnerClass>
package ${packageName};

<#if importList??>
<#list importList as importClass>
import ${importClass};
</#list>
</#if>

/**
 * @author ${author!"unknown"}
 * @date ${date}
 * @Description ${holderName}
 */
</#if>
<#if isInnerClass>public static class ${holderName}<#else>public class ${holderName}</#if> extends BaseRecycleViewHolder {

<#if viewList??>
<#list viewList as view>
    private ${view.typeName} ${view.fieldName};
</#list>
</#if>

    public ${holderName}(View itemView) {
        super(itemView);
        initViews();
    }

    private void initViews() {
<#if viewList??>
<#list viewList as view>
        this.${view.fieldName} = itemView.findViewById(R.id.${view.idName});
</#list>
</#if>
    }

    @Override
    public void bindViewHolder(ItemBaseBean bean, int position) {
        super.bindViewHolder(bean, position);
        if (bean == null) return;
        itemView.setTag(R.id.position, position);
        itemView.setTag(R.id.bean, bean);
    }
}