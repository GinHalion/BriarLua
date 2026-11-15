package github.daisukiKaffuChino;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import java.util.List;

@Keep
public class LuaPagerAdapter extends PagerAdapter {

    private final DataSetObservable mObservable = new DataSetObservable();
    private final List<View> pagerViews;
    private final List<String> titles;

    public LuaPagerAdapter(List<View> list) {
        this.pagerViews = list;
        this.titles = null;
    }

    public LuaPagerAdapter(List<View> list, List<String> titles) {
        this.pagerViews = list;
        this.titles = titles;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        if (titles != null && position >= 0 && position < titles.size()) {
            String title = titles.get(position);
            return title != null ? title : "No Title";
        }
        return "No Title";
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        if (object instanceof View) {
            container.removeView((View) object);
        }
    }

    @Override
    public int getCount() {
        return pagerViews.size();
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        View view = pagerViews.get(position);
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        container.addView(view);
        return view;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return POSITION_NONE;
    }

    @Override
    public void notifyDataSetChanged() {
        mObservable.notifyChanged();
        super.notifyDataSetChanged();
    }

    @Override
    public void registerDataSetObserver(@NonNull DataSetObserver observer) {
        mObservable.registerObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
        mObservable.unregisterObserver(observer);
    }

    public void add(View view) {
        pagerViews.add(view);
    }

    public void insert(int index, View view) {
        pagerViews.add(index, view);
    }

    public View remove(int index) {
        return pagerViews.remove(index);
    }

    public boolean remove(View view) {
        return pagerViews.remove(view);
    }

    public View getItem(int index) {
        return pagerViews.get(index);
    }

    public List<View> getData() {
        return pagerViews;
    }
}