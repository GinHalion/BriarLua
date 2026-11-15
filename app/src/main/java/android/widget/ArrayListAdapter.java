
package android.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.luajava.LuaException;
import com.luajava.LuaFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ArrayListAdapter<T> extends BaseAdapter implements Filterable {
    private ArrayList<T> mObjects;

    private final Object mLock = new Object();

    private int mResource;

    private int mDropDownResource;

    private int mFieldId = 0;

    private boolean mNotifyOnChange = true;

    private Context mContext;

    private ArrayList<T> mOriginalValues;
    private ArrayFilter mFilter;

    private LayoutInflater mInflater;
    private LuaFunction mLuaFilter;

    public ArrayListAdapter(Context context) {
        init(context, android.R.layout.simple_list_item_1, 0, new ArrayList<T>());
    }

    public ArrayListAdapter(Context context, T[] objects) {
        init(context, android.R.layout.simple_list_item_1, 0, Arrays.asList(objects));
    }

    public ArrayListAdapter(Context context, List<T> objects) {
        init(context, android.R.layout.simple_list_item_1, 0, objects);
    }


    public ArrayListAdapter(Context context, int textViewResourceId) {
        init(context, textViewResourceId, 0, new ArrayList<T>());
    }

    public ArrayListAdapter(Context context, int resource, int textViewResourceId) {
        init(context, resource, textViewResourceId, new ArrayList<T>());
    }

    public ArrayListAdapter(Context context, int textViewResourceId, T[] objects) {
        init(context, textViewResourceId, 0, Arrays.asList(objects));
    }

    public ArrayListAdapter(Context context, int resource, int textViewResourceId, T[] objects) {
        init(context, resource, textViewResourceId, Arrays.asList(objects));
    }

    public ArrayListAdapter(Context context, int textViewResourceId, List<T> objects) {
        init(context, textViewResourceId, 0, objects);
    }

    public ArrayListAdapter(Context context, int resource, int textViewResourceId, List<T> objects) {
        init(context, resource, textViewResourceId, objects);
    }

    public void add(T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(object);
            } else {
                mObjects.add(object);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(Collection<? extends T> collection) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.addAll(collection);
            } else {
                mObjects.addAll(collection);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(T... items) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                Collections.addAll(mOriginalValues, items);
            } else {
                Collections.addAll(mObjects, items);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(int index, T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(index, object);
            } else {
                mObjects.add(index, object);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.remove(object);
            } else {
                mObjects.remove(object);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(int idx) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.remove(idx);
            } else {
                mObjects.remove(idx);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.clear();
            } else {
                mObjects.clear();
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void sort(Comparator<? super T> comparator) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                Collections.sort(mOriginalValues, comparator);
            } else {
                Collections.sort(mObjects, comparator);
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        mNotifyOnChange = true;
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
    }

    private void init(Context context, int resource, int textViewResourceId, List<T> objects) {
        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResource = mDropDownResource = resource;
        mObjects = new ArrayList<T>(objects);
        mFieldId = textViewResourceId;
    }

    public Object getData() {
        return mObjects;
    }

    public Context getContext() {
        return mContext;
    }

    public int getCount() {
        return mObjects.size();
    }

    public T getItem(int position) {
        return mObjects.get(position);
    }

    public int getPosition(T item) {
        return mObjects.indexOf(item);
    }

    public long getItemId(int position) {
        return position + 1;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView, ViewGroup parent,
                                        int resource) {
        View view;
        TextView text;

        if (convertView == null) {
            view = mInflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }

        try {
            if (mFieldId == 0) {
                text = (TextView) view;
            } else {
                text = (TextView) view.findViewById(mFieldId);
            }
        } catch (ClassCastException e) {
            Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                    "ArrayAdapter requires the resource ID to be a TextView", e);
        }

        T item = getItem(position);
        if (item instanceof CharSequence) {
            text.setText((CharSequence) item);
        } else {
            text.setText(item.toString());
        }

        return view;
    }

    public void setDropDownViewResource(int resource) {
        this.mDropDownResource = resource;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mDropDownResource);
    }

    public static ArrayListAdapter<CharSequence> createFromResource(Context context,
                                                                    int textArrayResId, int textViewResId) {
        CharSequence[] strings = context.getResources().getTextArray(textArrayResId);
        return new ArrayListAdapter<CharSequence>(context, textViewResId, strings);
    }

    @Override
    public Filter getFilter() {
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        return mFilter;
    }

    public void filter(CharSequence constraint) {
        getFilter().filter(constraint);
    }

    public void setFilter(LuaFunction filter) {
        mLuaFilter = filter;
    }

    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();

            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<T>(mObjects);
                }
            } else if (TextUtils.isEmpty(prefix)) {
                results.values = new ArrayList<>(mOriginalValues);
                results.count = mOriginalValues.size();
                mOriginalValues = null;
                return results;
            }
            if (mLuaFilter != null) {
                final ArrayList<T> newValues = new ArrayList<T>();
                try {
                    mLuaFilter.call(new ArrayList<>(mOriginalValues), newValues, prefix);
                } catch (LuaException e) {
                    e.printStackTrace();
                }
                results.values = newValues;
                results.count = newValues.size();
                return results;
            }
            if (prefix == null || prefix.length() == 0) {
                ArrayList<T> list;
                synchronized (mLock) {
                    list = new ArrayList<T>(mOriginalValues);
                }
                results.values = list;
                results.count = list.size();
            } else {
                String prefixString = prefix.toString().toLowerCase();

                ArrayList<T> values;
                synchronized (mLock) {
                    values = new ArrayList<T>(mOriginalValues);
                }

                final int count = values.size();
                final ArrayList<T> newValues = new ArrayList<T>();

                for (int i = 0; i < count; i++) {
                    final T value = values.get(i);
                    final String valueText = value.toString().toLowerCase();

                    if (valueText.contains(prefixString)) {
                        newValues.add(value);
                    }
                }

                results.values = newValues;
                results.count = newValues.size();
            }

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mObjects = (ArrayList<T>) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    }
}
