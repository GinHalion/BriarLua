package com.zzzmode.apkeditor.axmleditor.editor;

import com.zzzmode.apkeditor.axmleditor.decode.AXMLDoc;
import com.zzzmode.apkeditor.axmleditor.decode.BTagNode;
import com.zzzmode.apkeditor.axmleditor.decode.BXMLNode;
import com.zzzmode.apkeditor.axmleditor.decode.StringBlock;
import com.zzzmode.apkeditor.axmleditor.utils.TypedValue;

public class UsesSdkEditor extends BaseEditor<UsesSdkEditor.EditorInfo> {

    public UsesSdkEditor(AXMLDoc doc) {
        super(doc);
    }

    @Override
    public String getEditorName() {
        return "uses-sdk";
    }

    @Override
    protected BXMLNode findNode() {
        BXMLNode manifest = doc.getManifestNode();
        if (manifest instanceof BTagNode) {
            for (BXMLNode child : ((BTagNode) manifest).getChildren()) {
                if (child instanceof BTagNode) {
                    BTagNode tag = (BTagNode) child;
                    String tagName = doc.getStringBlock().getStringFor(tag.getName());
                    if ("uses-sdk".equals(tagName)) {
                        return tag;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void editor() {
        BTagNode node = (BTagNode) findNode();
        if (node == null) return;

        StringBlock sb = doc.getStringBlock();
        BTagNode.Attribute[] attrs = node.getAttribute();

        for (BTagNode.Attribute attr : attrs) {
            String attrName = sb.getStringFor(attr.mName);
            if ("minSdkVersion".equals(attrName) && editorInfo.minSdkVersion > 0) {
                attr.setValue(TypedValue.TYPE_INT_DEC, editorInfo.minSdkVersion);
            } else if ("targetSdkVersion".equals(attrName) && editorInfo.targetSdkVersion > 0) {
                attr.setValue(TypedValue.TYPE_INT_DEC, editorInfo.targetSdkVersion);
            }
        }
    }

    @Override
    protected void registStringBlock(StringBlock sb) {
        sb.putString("uses-sdk");
        sb.putString("minSdkVersion");
        sb.putString("targetSdkVersion");
    }

    public static class EditorInfo {
        public final int minSdkVersion;
        public final int targetSdkVersion;

        public EditorInfo(int minSdkVersion, int targetSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            this.targetSdkVersion = targetSdkVersion;
        }
    }
}