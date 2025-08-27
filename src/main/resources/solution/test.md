报错解决方案
1.systemctl stop firewalld

2.取消终端代理
git config --global --unset http.proxy
git config --global --unset https.proxy