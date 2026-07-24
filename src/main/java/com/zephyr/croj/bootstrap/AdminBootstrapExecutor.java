package com.zephyr.croj.bootstrap;

@FunctionalInterface
interface AdminBootstrapExecutor {
    AdminBootstrapResult bootstrap(AdminBootstrapRequest request);
}
