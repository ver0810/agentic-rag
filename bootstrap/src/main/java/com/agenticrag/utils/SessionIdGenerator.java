package com.agenticrag.utils;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

public class SessionIdGenerator {

    public static String generate() {
        return IdWorker.getIdStr();
    }

}
