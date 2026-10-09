package ${packageName};

import android.os.Bundle;
import android.view.Gravity;
import androidx.annotation.NonNull;

import com.huimai365.R;
import com.huimai365.base.activity.BaseActivity;
import com.huimai365.base.activity.BaseDialog;

<#if importList??>
<#list importList as importClass>
import ${importClass};
</#list>
</#if>

/**
 * @author ${author!"unknown"}
 * @date ${date}
 * @Description ${dialogName}
 */
public class ${dialogName} extends BaseDialog {

    private BaseActivity mActivity;
${fieldsCode!}

    public ${dialogName}(@NonNull BaseActivity context) {
        super(context, R.style.dialog_dark);
        this.mActivity = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});

        setCancelable(false);
        setCanceledOnTouchOutside(false);
        initWindowSize(Gravity.CENTER, -2, -2, false);

${initViewsCode!}
    }
}