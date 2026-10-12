<#if !isInnerClass>
package ${packageName};
</#if>

<#if importList??>
<#list importList as importClass>
import ${importClass};
</#list>
</#if>

import com.huimai365.R;
import com.huimai365.compere.bean.ItemBaseBean;
import com.huimai365.goods.adapter.holder.BaseRecycleViewHolder;

/**
 * @author ${author!"unknown"}
 * @date ${date!"unknown"}
 * @Description ${holderName!"unknown"}
 */
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