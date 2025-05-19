package com.kulipai.luahook;

import com.kulipai.luahook.ShellResult;

interface IUserService {
    ShellResult exec(String cmd);
    void destroy();
    void exit();
}
