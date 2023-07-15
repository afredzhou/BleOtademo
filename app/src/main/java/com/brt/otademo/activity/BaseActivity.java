package com.brt.otademo.activity;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.brt.otademo.R;


public class BaseActivity extends AppCompatActivity {
    private TextView commonTitleTv;
    private Toolbar commonTitleTb;
    private RelativeLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        initView();
        setSupportActionBar(commonTitleTb);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    private void initView() {
        commonTitleTb = findViewById(R.id.common_title_tb);
        commonTitleTv = findViewById(R.id.common_title_tv);
        content = findViewById(R.id.content);
    }

    /**
     * 重新设置 toolbar
     * @param layout
     */
    public void setToolbar(int layout) {
        hideToolbar();
        commonTitleTb =  content.findViewById(layout);
        setSupportActionBar(commonTitleTb);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    /**
     * 隐藏 toolbar
     */
    public void hideToolbar() {
        commonTitleTb.setVisibility(View.GONE);
    }

    /**
     * 设置 menu 按钮点击事件
     * @param onclick
     */
    public void setToolBarMenuOnclick(Toolbar.OnMenuItemClickListener onclick) {
        commonTitleTb.setOnMenuItemClickListener(onclick);
    }

    /**
     * 设置 back 键
     */
    public void setBackArrow() {
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_back);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        commonTitleTb.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * toolbar 下方区域的内容
     * @param layoutId
     */
    public void setContentLayout(int layoutId) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View contentView = inflater.inflate(layoutId, null);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,  ViewGroup.LayoutParams.MATCH_PARENT);
        content.addView(contentView, params);
    }

    /**
     * 设置标题
     * @param title
     */
    public void setTitle(String title) {
        if (!TextUtils.isEmpty(title)) {
            commonTitleTv.setText(title);
        }
    }

    /**
     * 设置标题
     * @param resId
     */
    public void setTitle(int resId) {
        commonTitleTv.setText(resId);
    }
}
