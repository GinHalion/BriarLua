package com.zzzmode.apkeditor.axmleditor.editor;

import com.zzzmode.apkeditor.axmleditor.decode.AXMLDoc;
import com.zzzmode.apkeditor.axmleditor.decode.BTagNode;
import com.zzzmode.apkeditor.axmleditor.decode.BXMLNode;
import com.zzzmode.apkeditor.axmleditor.decode.StringBlock;
import com.zzzmode.apkeditor.axmleditor.utils.TypedValue;

import java.util.List;

public class LabelGlobalEditor extends BaseEditor<String> {

    public LabelGlobalEditor(AXMLDoc doc) {
        super(doc);
    }

    @Override
    public String getEditorName() {
        return "label-global";
    }

    @Override
protected void editor() {
    BXMLNode application = doc.getApplicationNode();
    if (application == null) return;

    StringBlock sb = doc.getStringBlock();
    int labelNameIndex = sb.getStringMapping("label");
    if (labelNameIndex == -1) return;

    int newLabelValueIndex = sb.addString(editorInfo);

    if (application instanceof BTagNode) {
        BTagNode appTag = (BTagNode) application;
        appTag.setAttrStringForKey(labelNameIndex, newLabelValueIndex);
    }

    List<BXMLNode> children = application.getChildren();
    for (BXMLNode node : children) {
        if (node instanceof BTagNode) {
            BTagNode tag = (BTagNode) node;
            BTagNode.Attribute[] attrs = tag.getAttribute();
            for (BTagNode.Attribute attr : attrs) {
                if (attr.mName == labelNameIndex && (attr.mType >> 24) == TypedValue.TYPE_STRING) {
                    attr.setValue(TypedValue.TYPE_STRING, newLabelValueIndex);
                    break;
                }
            }
        }
    }
}


    @Override
    protected BXMLNode findNode() {
        return doc.getApplicationNode();
    }

    @Override
    protected void registStringBlock(StringBlock sb) {
        namespace = sb.putString(NAME_SPACE);
        sb.putString("label");
    }
}
