package ${packageName};

<#-- 动态导入包名列表 -->
<#if importList??>
<#list importList as importClass>
import ${importClass};
</#list>
</#if>

/**
 * @author ${author!"unknown"}
 * @date ${date}
 * @Description ${fragmentName}
 */
public class ${fragmentName} extends BaseFragment implements View.OnClickListener {

<#if fieldsCode?? && fieldsCode?has_content>
${fieldsCode}<#t>
</#if>

    //<editor-fold desc="-- 生命周期">
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.${layoutName}, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    //</editor-fold>

    //<editor-fold desc="-- 方法重写">
    @Override
    public void onClick(View v) {
<#if clickViewsCode?? && clickViewsCode?has_content>
${clickViewsCode}
<#else>
        // onClick
</#if>
    }
    //</editor-fold>

    //<editor-fold desc="-- 内部方法">
    private void initViews(View view) {
<#if initViewsCode?? && initViewsCode?has_content>
${initViewsCode}
<#else>
        // findView
</#if>
    }
    //</editor-fold>

    //<editor-fold desc="-- 外部方法">

    //</editor-fold>

    //<editor-fold desc="-- 网络请求">
    private void loadData() {
<#if requestCode?? && requestCode?has_content>
${requestCode}
<#else>
        // loadData
</#if>
    }

    @Override
    protected void onEventMainThread(MessageBean bean) {
        super.onEventMainThread(bean);
<#if responseCode?? && responseCode?has_content>
${responseCode}
<#else>
        // onEventMainThread
</#if>
    }
    //</editor-fold>
}