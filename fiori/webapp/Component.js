sap.ui.define([
    "sap/ui/core/UIComponent"
], function (UIComponent) {
    "use strict";

    return UIComponent.extend("digital.forsign.cockpit.Component", {
        metadata: { manifest: "json" },

        init: function () {
            UIComponent.prototype.init.apply(this, arguments);
        },

        getCpiBaseUrl: function () {
            return this.getManifestEntry("/sap.ui5/config/forsignCpiBaseUrl");
        }
    });
});
