/*
 * Copyright (C) 2023 BackgroundOpt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.venus.backgroundopt.xposed.point.handler;

import com.venus.backgroundopt.xposed.hook.base.PackageHook;
import com.venus.backgroundopt.xposed.point.miui.PowerStateMachineHook;
import com.venus.backgroundopt.xposed.point.miui.ProcessManagerHook;
import com.venus.backgroundopt.xposed.point.miui.SleepModeControllerNewHook;

import io.github.libxposed.api.XposedModuleInterface;

public class PowerKeeperHookHandler extends PackageHook {
    public PowerKeeperHookHandler(XposedModuleInterface.PackageLoadedParam packageParam) {
        super(packageParam);
    }

    @Override
    public void hook(XposedModuleInterface.PackageLoadedParam packageParam) {
        ClassLoader classLoader = packageParam.getClassLoader();

        new PowerStateMachineHook(classLoader);
        new ProcessManagerHook(classLoader);
        new SleepModeControllerNewHook(classLoader);
    }
}
