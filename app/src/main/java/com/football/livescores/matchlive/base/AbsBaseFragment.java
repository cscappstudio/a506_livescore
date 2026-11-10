package com.football.livescores.matchlive.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;


public abstract class AbsBaseFragment<V extends ViewDataBinding> extends Fragment {
    protected V binding;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initView();
        super.onViewCreated(view, savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
       if(binding==null){
           binding= DataBindingUtil.inflate(inflater,getLayout(),null,false);
           binding.setLifecycleOwner(this);
       }
       return binding.getRoot();
    }
    public abstract  int getLayout();
    public abstract void initView();
}
