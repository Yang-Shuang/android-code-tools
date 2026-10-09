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
<#if isInnerClass>public class ${holderName}<#else>public class ${holderName}</#if> extends BaseRecycleViewHolder {

${fieldsCode!}
    public ${holderName}(View itemView) {
        super(itemView);
        initViews();
    }

    private void initViews() {
${initViewsCode!}
    }

    @Override
    public void bindViewHolder(ItemBaseBean bean, int position) {
        super.bindViewHolder(bean, position);
        if (bean == null) return;
        itemView.setTag(R.id.position, position);
        itemView.setTag(R.id.bean, bean);
    }
}