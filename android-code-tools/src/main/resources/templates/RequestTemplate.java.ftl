        XXXRequest request = new XXXRequest();
        if (request.isRunning(REQUEST_TAG_XXX)) return;
        showLoading();
        JsonObject params = new JsonObject();
        if (checkUserLogin()) {
            params.addProperty("usrId", "xxxx");
        }
        request.getXXXxxx(params.toString(), addRequestTag(REQUEST_TAG_XXX));