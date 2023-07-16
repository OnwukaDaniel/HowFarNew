package com.azur.howfar.models

import com.azur.howfar.models.MessageType.TEXT
import kotlin.reflect.KClass

data class ChatDataNotification(
    var chat: String = "",
    var body: String = "",
    var groupUid: String = "",
    var view: String = "",
    var image: String = "",
    var title: String = "",
)

fun <T : Any> mapToObject(map: Map<String, Any>, clazz: KClass<T>) : T {
    //Get default constructor
    val constructor = clazz.constructors.first()

    //Map constructor parameters to map values
    val args = constructor.parameters.associateWith { map[it.name] }

    //return object from constructor call
    return constructor.callBy(args)
}