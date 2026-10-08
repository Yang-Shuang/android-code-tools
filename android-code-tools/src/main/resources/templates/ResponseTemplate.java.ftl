        if (REQUEST_TAG_XXX.equals(bean.getTag())) {
            hideLoading();
            if (MessageBean.RequestStatus.REQUEST_OK == bean.getStatus()) {
                XxxBean targetBean = (XxxBean) bean.getObj();
                // 处理成功逻辑
            } else if (!TextUtils.isEmpty(bean.getShowErrorMsg())) {
                tips(bean.getShowErrorMsg());
            } else if (!TextUtils.isEmpty(bean.getErrorMsg())) {
                tips(bean.getErrorMsg());
            }
        }