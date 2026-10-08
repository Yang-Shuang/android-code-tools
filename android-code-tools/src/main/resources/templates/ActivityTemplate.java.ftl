package ${packageName};

<#-- 1. 动态渲染所有 import（包含基类、View控件类、MessageBean 等） -->
<#if importList??>
<#list importList as importClass>
import ${importClass};
</#list>
</#if>

/**
 * @author ${author!"unknown"}
 * @date ${date}
 * @Description ${activityName}
 */
public class ${activityName} extends BaseActivity implements View.OnClickListener {

<#-- 2. 注入 private View 变量声明 -->
<#if fieldsCode??>
${fieldsCode}
</#if>

    //<editor-fold desc="-- 生命周期">
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        DensityUtil.setDefaultDisplay(this);
        setContentView(R.layout.${layoutName});
        getArguments();
        initViews();
        addLoading(false);
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    //</editor-fold>

    //<editor-fold desc="-- 方法重写">
    @Override
    public void onClick(View v) {
<#if clickViewsCode?? && clickViewsCode?has_content>
${clickViewsCode}<#t>
<#else>
        // TODO onClick
</#if>
    }
    //</editor-fold>

    //<editor-fold desc="-- 内部方法">
    private void getArguments() {

    }

<#-- 4. 注入生成的 initViews() 方法（含 findViewById） -->
    private void initViews() {
<#if initViewsCode?? && initViewsCode?has_content>
${initViewsCode}<#t>
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
${requestCode}<#t>
<#else>
        // loadData
</#if>
    }

    @Override
    protected void onEventMainThread(MessageBean bean) {
        super.onEventMainThread(bean);
<#if responseCode?? && responseCode?has_content>
${responseCode}<#t>
<#else>
        // onEventMainThread
</#if>
    }
    //</editor-fold>
}