sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/ui/model/json/JSONModel",
    "sap/m/MessageToast",
    "sap/m/MessageBox"
], function (Controller, JSONModel, MessageToast, MessageBox) {
    "use strict";

    return Controller.extend("digital.forsign.cockpit.controller.Cockpit", {

        onInit: function () {
            this.getView().setModel(new JSONModel({}), "status");
        },

        _baseUrl: function () {
            return this.getOwnerComponent().getCpiBaseUrl();
        },

        _operationId: function () {
            return this.byId("operationIdInput").getValue();
        },

        onLoadOperation: function () {
            var sOperationId = this._operationId();
            if (!sOperationId) {
                MessageToast.show(this._i18n("enterOperationId"));
                return;
            }
            var that = this;
            this.getView().setBusy(true);
            fetch(this._baseUrl() + "/forsign/v1/operations/status?operationId=" +
                    encodeURIComponent(sOperationId), { headers: { Accept: "application/json" } })
                .then(function (response) {
                    if (!response.ok) {
                        return response.json().then(function (error) {
                            throw new Error((error.error && error.error.message) || ("HTTP " + response.status));
                        });
                    }
                    return response.json();
                })
                .then(function (data) {
                    that.getView().getModel("status").setData(data);
                })
                .catch(function (error) {
                    MessageBox.error(that._i18n("loadError") + "\n" + error.message);
                })
                .finally(function () {
                    that.getView().setBusy(false);
                });
        },

        onDownloadDocument: function () {
            var that = this;
            var sOperationId = this._operationId();
            MessageBox.show(this._i18n("documentIdPrompt"), {
                title: this._i18n("downloadButton"),
                actions: [MessageBox.Action.OK, MessageBox.Action.CANCEL],
                initialFocus: MessageBox.Action.OK,
                onClose: function (sAction) {
                    if (sAction === MessageBox.Action.OK) {
                        that._download(sOperationId, "1");
                    }
                }
            });
        },

        _download: function (sOperationId, sDocumentId) {
            var that = this;
            this.getView().setBusy(true);
            fetch(this._baseUrl() + "/forsign/v1/operations/download?operationId=" +
                    encodeURIComponent(sOperationId) + "&documentId=" + encodeURIComponent(sDocumentId),
                    { headers: { Accept: "application/json" } })
                .then(function (response) {
                    if (!response.ok) { throw new Error("HTTP " + response.status); }
                    return response.json();
                })
                .then(function (data) {
                    var aBytes = Uint8Array.from(atob(data.base64Content), function (c) {
                        return c.charCodeAt(0);
                    });
                    var oBlob = new Blob([aBytes], { type: data.contentType || "application/pdf" });
                    var oLink = document.createElement("a");
                    oLink.href = URL.createObjectURL(oBlob);
                    oLink.download = data.fileName || ("operacao-" + sOperationId + ".pdf");
                    oLink.click();
                    URL.revokeObjectURL(oLink.href);
                })
                .catch(function (error) {
                    MessageBox.error(that._i18n("downloadError") + "\n" + error.message);
                })
                .finally(function () {
                    that.getView().setBusy(false);
                });
        },

        formatStatusState: function (sStatus) {
            switch (sStatus) {
                case "Completed": return "Success";
                case "Canceled":
                case "Expired": return "Error";
                case "InProgress":
                case "WaitingSignatures":
                case "WaitingForms":
                case "WaitingNotify":
                case "CheckingAttachments": return "Warning";
                default: return "None";
            }
        },

        formatMemberState: function (sStatus) {
            switch (sStatus) {
                case "Completed": return "Success";
                case "Canceled": return "Error";
                default: return "Warning";
            }
        },

        _i18n: function (sKey) {
            return this.getView().getModel("i18n").getResourceBundle().getText(sKey);
        }
    });
});
