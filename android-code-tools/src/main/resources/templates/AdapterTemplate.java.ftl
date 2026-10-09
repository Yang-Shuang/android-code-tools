package ${packageName};

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.huimai365.goods.adapter.BaseRecycleViewAdapter;
import com.huimai365.goods.adapter.holder.BaseRecycleViewHolder;

import java.util.ArrayList;
import java.util.List;

<#-- 动态导入列表 -->
<#if importList??>
<#list importList as importClass>
import ${importClass};
</#list>
</#if>

/**
 * @author ${author!"unknown"}
 * @date ${date}
 * @Description ${adapterName}
 */
public class ${adapterName} extends BaseRecycleViewAdapter<BaseRecycleViewHolder> {

    private ArrayList<${beanName}> mList = new ArrayList<>();

    @NonNull
    @Override
    public BaseRecycleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        if (viewType == TYPE_XXX) {
//            return new XXXItemHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_xxx, parent, false));
//        }
        return null;
    }

    @Override
    public void onBindBaseHolder(BaseRecycleViewHolder holder, int position) {
        holder.bindViewHolder(mList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    public void clear() {
        int size = mList.size();
        if (size <= 0) return;
        mList.clear();
        notifyItemRangeRemoved(0, size);
    }

    public void add(${beanName} bean) {
        if (bean == null) return;
        int size = mList.size();
        mList.add(bean);
        notifyItemInserted(size);
    }

    public void add(List<${beanName}> beans) {
        if (beans == null || beans.isEmpty()) return;
        int beansSize = beans.size();
        int listSize = mList.size();
        mList.addAll(beans);
        notifyItemRangeInserted(listSize, beansSize);
    }
}