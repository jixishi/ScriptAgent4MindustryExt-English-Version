@file:Suppress("unused")

package coreLibrary.lib

/**
 * Can be used to parse strings with variables
 * Can also be used to share data or interfaces between scripts
 * When exposing data or interfaces, pay attention to the life cycle of the type
 */

import cf.wayzer.placehold.DynamicVar
import cf.wayzer.placehold.PlaceHoldApi
import cf.wayzer.placehold.PlaceHoldContext
import cf.wayzer.placehold.TypeBinder
import cf.wayzer.script_agent.IBaseScript
import cf.wayzer.script_agent.util.DSLBuilder
import kotlin.reflect.KProperty

typealias PlaceHoldString = PlaceHoldContext

object PlaceHold {
    class PlaceHoldKey<T>(val name: String,private val cls:Class<T>){
        operator fun getValue(thisRef: Any?,prop:KProperty<*>):T{
            val v = PlaceHoldApi.GlobalContext.getVar(name)
            if(cls.isInstance(v))return cls.cast(v)
            error("Can't get globalVar: $name get $v")
        }
    }

    class TypePlaceHoldKey<R>(val name: String, private val cls: Class<R>) {
        operator fun <T : Any> getValue(thisRef: T, prop: KProperty<*>): R {
            val v = PlaceHoldApi.GlobalContext.typeResolve(thisRef, name)
            if (cls.isInstance(v)) return cls.cast(v)
            error("Can't get typeVar $name: FROM $thisRef GET $v")
        }
    }

    class ScriptTypeBinder<T : Any>(private val script: IBaseScript, private val namePrefix: String, private val binder: TypeBinder<T>) {
        fun registerChild(key: String, desc: String, body: DynamicVar<T, Any>) {
            script.registeredVars["$namePrefix.$key"] = desc
            binder.registerChild(key, body)
        }

        fun registerToString(desc: String, body: DynamicVar<T, String>) {
            script.registeredVars["$namePrefix.toString"] = desc
            binder.registerToString(body)
        }
    }

    // Map of name to description
    val IBaseScript.registeredVars by DSLBuilder.dataKeyWithDefault { mutableMapOf<String, String>() }

    /**
     * @param v support [cf.wayzer.placehold.DynamicVar] even [PlaceHoldString] or any value
     */
    fun register(script: IBaseScript, name: String, desc: String, v: Any?) {
        PlaceHoldApi.registerGlobalVar(name, v)
        script.registeredVars[name] = desc
    }

    /**
     * @see TypeBinder
     * @param desc describe what you want to add
     */
    @Deprecated("use registerForType(script): ScriptTypeBinder<T> instead")
    inline fun <reified T : Any> registerForType(script: IBaseScript, desc: String): TypeBinder<T> {
        script.registeredVars["Type@${T::class.java.simpleName}"] = desc
        return PlaceHoldApi.typeBinder()
    }

    /**
     * @see TypeBinder
     */
    inline fun <reified T : Any> registerForType(script: IBaseScript): ScriptTypeBinder<T> {
        return ScriptTypeBinder(script, "Type@${T::class.java.simpleName}", PlaceHoldApi.typeBinder())
    }

    /**
     * @sample
     * val tps by PlaceHold.reference<Int>("tps")
     * println(tps) //get variable
     */
    inline fun <reified T> reference(name: String) = PlaceHoldKey(name, T::class.java)

    /**
     * @sample
     * val Player.money by PlaceHold.referenceForType<Int>("money")
     * player.money //get variable
     */
    inline fun <reified R> referenceForType(name: String) = TypePlaceHoldKey(name,R::class.java)
}

/**
 * @param arg values support [cf.wayzer.placehold.DynamicVar] even [PlaceHoldString] or any value
 */
fun String.with(vararg arg: Pair<String, Any>): PlaceHoldString = PlaceHoldApi.getContext(this, arg.toMap())

/**
 * @see PlaceHold.register
 */
fun IBaseScript.registerVar(name: String, desc: String, v: Any?) = PlaceHold.register(this,name, desc, v)

/**
 * @see PlaceHold.registerForType
 */
@Suppress("DEPRECATION")
@Deprecated("use registerVarForType() instead", ReplaceWith("IBaseScript.registerVarForType()"))
inline fun <reified T : Any> IBaseScript.registerVarForType(desc: String) = PlaceHold.registerForType<T>(this, desc)

/**
 * @see PlaceHold.registerForType
 */
inline fun <reified T : Any> IBaseScript.registerVarForType() = PlaceHold.registerForType<T>(this)