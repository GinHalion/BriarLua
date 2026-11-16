package com.zzzmode.apkeditor.axmleditor.editor;

import com.zzzmode.apkeditor.axmleditor.decode.AXMLDoc;
import com.zzzmode.apkeditor.axmleditor.decode.BTagNode;
import com.zzzmode.apkeditor.axmleditor.decode.BXMLNode;
import com.zzzmode.apkeditor.axmleditor.decode.StringBlock;
import com.zzzmode.apkeditor.axmleditor.utils.TypedValue;

import java.util.List;

public class MetaDataEditor extends BaseEditor<MetaDataEditor.EditorInfo> {

    public MetaDataEditor(AXMLDoc doc) {
        super(doc);
        setEditor(NAME,VALUE);
    }

    private int meta_data;


    @Override
    public String getEditorName() {
        return NODE_METADATA;
    }

    @Override
    protected void editor() {
        BXMLNode application = doc.getApplicationNode();
        List<BXMLNode> children = application.getChildren();

        BTagNode meta_data = (BTagNode) findNode();

        if(meta_data != null){
            meta_data.setAttrStringForKey(attr_value, editorInfo.metaValue_Index);
        }else{
            BTagNode.Attribute name_attr = new BTagNode.Attribute(namespace, attr_name, TypedValue.TYPE_STRING);
            name_attr.setString(editorInfo.metaName_Index);
            BTagNode.Attribute value_attr = new BTagNode.Attribute(namespace, attr_value, TypedValue.TYPE_STRING);
            value_attr.setString(editorInfo.metaValue_Index);

            meta_data = new BTagNode(-1, this.meta_data);
            meta_data.setAttribute(name_attr);
            meta_data.setAttribute(value_attr);
            children.add(meta_data);
        }
        doc.getStringBlock().setString(editorInfo.metaValue_Index, editorInfo.metaValue);
    }

    @Override
    protected BXMLNode findNode() {
        BXMLNode application = doc.getApplicationNode();
        List<BXMLNode> children = application.getChildren();

        BTagNode meta_data = null;

        end:for(BXMLNode node : children){
            BTagNode m = (BTagNode)node;
            if((this.meta_data == m.getName()) && (m.getAttrStringForKey(attr_name) == editorInfo.metaName_Index)){
                meta_data = m;
                break end;
            }
        }
        return meta_data;
    }


    @Override
    protected void registStringBlock(StringBlock sb) {
        namespace = sb.putString(NAME_SPACE);
        meta_data = sb.putString(NODE_METADATA);

        attr_name = sb.putString(NAME);
        attr_value = sb.putString(VALUE);



        editorInfo.metaName_Index=sb.putString(editorInfo.metaName);

        editorInfo.metaValue_Index=sb.addString(editorInfo.metaValue);

    }

    public static class EditorInfo{
        private String metaName;
        private String metaValue;

        private int metaName_Index;
        private int metaValue_Index;

        private boolean metaNameHasEditor;
        private boolean metaValueHasEditor;

        public EditorInfo(){}

        public EditorInfo(String metaName, String metaValue) {
            this.metaName = metaName;
            this.metaValue = metaValue;
        }
    }
}
