/*
 * Copyright (C) 2023-2024 BackgroundOpt
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

package com.venus.backgroundopt.xposed.bridge

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

abstract class XC_MethodHook {
    abstract class MethodHookParam(
        val chain: Chain
    ) {
        val thisObject: Any?
            get() = chain.thisObject
        val args: List<Any?>
            get() = chain.args
        var result: Any?
            get() = _result
            set(value) {
                _result = value
                _resultSet = true
            }
        var throwable: Throwable?
            get() = _throwable
            set(value) {
                _throwable = value
                _throwableSet = true
            }

        private var _result: Any? = null
        private var _resultSet = false
        private var _throwable: Throwable? = null
        private var _throwableSet = false

        fun getArg(index: Int): Any? = chain.getArg(index)

        fun getArgs(): Array<Any?> = chain.args.toTypedArray()

        fun setArgs(vararg args: Any?) {
            throw UnsupportedOperationException("Use chain.proceedWith() instead")
        }

        fun getExtraCallbackExtra(key: String): Any? = extras[key]

        private val extras = mutableMapOf<String, Any>()

        fun setExtraCallbackExtra(key: String, value: Any?) {
            if (value == null) {
                extras.remove(key)
            } else {
                extras[key] = value
            }
        }
    }

    open fun beforeHookedMethod(param: MethodHookParam) {}

    open fun afterHookedMethod(param: MethodHookParam) {}

    class Unhook(private val handle: HookHandle) {
        fun unhook() {
            handle.unhook()
        }
    }
}

abstract class XC_MethodReplacement {
    abstract fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any?

    companion object {
        val DO_NOTHING: XC_MethodReplacement = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any? {
                param.chain.proceed()
                return null
            }
        }
    }
}

object XC_LoadPackage {
    class LoadPackageParam(
        val packageName: String,
        val processName: String,
        val classLoader: ClassLoader,
        val appInfo: Any?
    )

    interface HookLoadPackage {
        fun handleLoadPackage(param: LoadPackageParam)
    }
}

fun XC_MethodHook.toHooker(before: Boolean = true): Hooker {
    return Hooker { chain ->
        val param = MethodHookParam(chain)
        try {
            if (before) {
                this.beforeHookedMethod(param)
                val result = chain.proceed()
                param.result = result
                this.afterHookedMethod(param)
                if (param.throwableSet) {
                    throw param.throwable!!
                }
                if (param.resultSet) {
                    return@Hooker param.result
                }
                return@Hooker result
            } else {
                val result = chain.proceed()
                param.result = result
                this.afterHookedMethod(param)
                if (param.throwableSet) {
                    throw param.throwable!!
                }
                if (param.resultSet) {
                    return@Hooker param.result
                }
                return@Hooker result
            }
        } catch (t: Throwable) {
            param.throwable = t
            this.afterHookedMethod(param)
            if (param.throwableSet) {
                throw param.throwable!!
            }
            throw t
        }
    }
}

fun XC_MethodReplacement.toHooker(): Hooker {
    return Hooker { chain ->
        val param = XC_MethodHook.MethodHookParam(chain)
        this.replaceHookedMethod(param)
    }
}

fun hookMethod(
    className: String,
    classLoader: ClassLoader,
    methodName: String?,
    vararg paramTypes: Any?,
    hook: XC_MethodHook
): XC_MethodHook.Unhook {
    val clazz = Class.forName(className, false, classLoader)
    val method = findMethodRecursive(clazz, methodName!!, paramTypes.map { (it as? Class<*>) ?: it.javaClass }.toTypedArray())
    method.isAccessible = true

    val xposed = XposedBridge.xposedInterface
    val handle = xposed.hook(method)
        .setPriority(XposedInterface.PRIORITY_DEFAULT)
        .intercept(hook.toHooker())

    return XC_MethodHook.Unhook(handle)
}

fun hookMethod(
    className: String,
    classLoader: ClassLoader,
    methodName: String?,
    hook: XC_MethodHook
): XC_MethodHook.Unhook {
    return hookMethod(className, classLoader, methodName, emptyArray<Any>(), hook)
}

fun hookConstructor(
    className: String,
    classLoader: ClassLoader,
    vararg paramTypes: Any?,
    hook: XC_MethodHook
): XC_MethodHook.Unhook {
    val clazz = Class.forName(className, false, classLoader)
    val constructor = clazz.getDeclaredConstructor(*paramTypes.map { (it as? Class<*>) ?: it.javaClass }.toTypedArray())
    constructor.isAccessible = true

    val xposed = XposedBridge.xposedInterface
    val handle = xposed.hook(constructor)
        .setPriority(XposedInterface.PRIORITY_DEFAULT)
        .intercept(hook.toHooker(before = false))

    return XC_MethodHook.Unhook(handle)
}

private fun findMethodRecursive(clazz: Class<*>, methodName: String, paramTypes: Array<Class<*>>): Method {
    var c: Class<*> = clazz
    while (c != null && c != Any::class.java) {
        try {
            return c.getDeclaredMethod(methodName, *paramTypes)
        } catch (e: NoSuchMethodException) {
            c = c.superclass
        }
    }
    throw NoSuchMethodError("Method $methodName not found in $clazz")
}

object XposedHelpers {
    @Throws(ClassNotFoundException::class)
    fun findClass(name: String, classLoader: ClassLoader): Class<*> {
        return Class.forName(name, false, classLoader)
    }

    fun findClassIfExists(name: String, classLoader: ClassLoader): Class<*>? {
        return try {
            findClass(name, classLoader)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        vararg params: Any?,
        hook: XC_MethodHook
    ): XC_MethodHook.Unhook {
        val paramTypes = params.dropLast(1).toTypedArray()
        return hookMethod(className, classLoader, methodName, *paramTypes, hook = hook)
    }

    fun findAndHookConstructor(
        className: String,
        classLoader: ClassLoader,
        vararg params: Any?,
        hook: XC_MethodHook
    ): XC_MethodHook.Unhook {
        val paramTypes = params.dropLast(1).toTypedArray()
        return hookConstructor(className, classLoader, *paramTypes, hook = hook)
    }

    fun findMethodBestMatch(
        clazz: Class<*>,
        methodName: String,
        vararg args: Any?
    ): Method {
        val paramTypes = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        return findMethodBestMatch(clazz, methodName, paramTypes)
    }

    fun findMethodBestMatch(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<out Class<*>>
    ): Method {
        return findMethodRecursive(clazz, methodName, parameterTypes)
    }

    fun findField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field {
        var field: java.lang.reflect.Field? = null
        var cl: Class<*> = clazz
        while (cl != null && cl != Any::class.java) {
            try {
                field = cl.getDeclaredField(fieldName)
                break
            } catch (e: java.lang.NoSuchFieldException) {
                cl = cl.superclass
            }
        }
        if (field == null) {
            throw NoSuchFieldError("Field $fieldName not found in $clazz")
        }
        field.isAccessible = true
        return field
    }

    fun getParameterTypes(vararg args: Any?): Array<Class<*>> {
        return args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val constructor = clazz.getDeclaredConstructor(*getParameterTypes(*args))
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    fun newInstance(
        clazz: Class<*>,
        paramTypes: Array<out Class<*>>,
        vararg args: Any?
    ): Any {
        val constructor = clazz.getDeclaredConstructor(*paramTypes)
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }
}
