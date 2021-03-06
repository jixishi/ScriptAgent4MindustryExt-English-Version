package wayzer

import cf.wayzer.placehold.PlaceHoldApi
import coreLibrary.lib.event.PermissionRequestEvent

name = "Permission Management System"

var groups by config.key(
    mapOf(
        "@default" to listOf("wayzer.ext.observer", "wayzer.ext.history"),
        "@admin" to listOf(
            "wayzer.admin.ban", "wayzer.info.other", "wayzer.vote.ban",
            "wayzer.maps.host", "wayzer.maps.load", "wayzer.ext.team.change",
        ),
    ),
    "Permission settings", "Special groups are:@default,@admin,@lvl0,@lvl1,etc.,userqq can be a separate group", "The value is the permission, the group starts with @, and the end wildcard is supported. *"
)

fun hasPermission(permission: String, list: List<String>): Boolean {
    list.forEach {
        when {
            it.startsWith("@") -> {
                if (checkGroup(permission, it))
                    return true
            }
            it.endsWith(".*") -> {
                if (permission.startsWith(it.removeSuffix("*")))
                    return true
            }
            else -> {
                if (permission == it) return true
            }
        }
    }
    return false
}

fun checkGroup(permission: String, groupName: String) =
    groups[groupName]?.let { hasPermission(permission, it) } ?: false

listenTo<PermissionRequestEvent> {
    if (context.player == null) return@listenTo//console
    try {
        fun check(body: () -> Boolean) {
            if (body()) {
                result = true
                CommandInfo.Return()
            }
        }
        check { checkGroup(permission, "@default") }
        val uuid = context.player!!.uuid()
        check { checkGroup(permission, uuid) }
        val profile = PlayerData[uuid].profile ?: return@listenTo
        check { checkGroup(permission, "qq${profile.qq}") }
        val level = (PlaceHoldApi.GlobalContext.typeResolve(profile, "level") ?: return@listenTo) as Int
        for (lvl in level downTo 0) {
            check { checkGroup(permission, "@lvl$lvl") }
        }
    } catch (e: CommandInfo.Return) {
    }
}

command("permission", "Permission system configuration") {
    permission = "wayzer.permission"
    usage = "<group> <add/list/remove/delGroup> [permission]"
    onComplete {
        onComplete(0) { groups.keys.toList() }
        onComplete(1) { listOf("add", "list", "remove", "addGroup") }
    }
    body {
        if (arg.isEmpty()) returnReply("Current group: {list}".with("list" to groups.keys))
        if (arg.size < 2) replyUsage()
        val group = arg[0]
        when (arg[1].toLowerCase()) {
            "add" -> {
                if (arg.size < 3) returnReply("[red]Please enter the permissions you need to add or remove".with())
                val now = groups[group].orEmpty()
                if (arg[2] !in now)
                    groups = groups + (group to (now + arg[2]))
                returnReply(
                    "[green]{op} permission {permission} to group {group}".with(
                        "op" to "添加", "permission" to arg[2], "group" to group
                    )
                )
            }
            "remove" -> {
                if (arg.size < 3) returnReply("[red]Please enter the permissions you need to add or remove".with())
                val now = groups[group].orEmpty()
                if (arg[2] in now)
                    groups = groups + (group to (now - arg[2]))
                returnReply(
                    "[green]{op} permission {permission} to group {group}".with(
                        "op" to "移除", "permission" to arg[2], "group" to group
                    )
                )
            }
            "list" -> {
                val now = groups[group].orEmpty()
                returnReply(
                    "The [green]group{group} currently has permissions: []\n{list}".with(
                        "group" to group, "list" to now.toString()
                    )
                )
            }
            "delgroup" -> {
                val now = groups[group].orEmpty()
                if (group in groups)
                    groups = groups - group
                returnReply(
                    "[yellow] Remove permission group {group}, which originally contained:{list}".with(
                        "group" to group, "list" to now.toString()
                    )
                )
            }
            else -> replyUsage()
        }
    }
}

command("madmin", "List or add delete management") {
    this.usage = "[uuid/qq]"
    this.permission = "wayzer.permission.admin"
    body {
        val uuid = arg.getOrNull(0)
        if (uuid == null) {
            val list = groups.filter { it.value.contains("@admin") }.keys.joinToString()
            returnReply("Admins: {list}".with("list" to list))
        } else {
            val isQQ = uuid.length > 5 && uuid.toLongOrNull() != null
            val key = if (isQQ) "qq$uuid" else uuid
            val now = groups[key].orEmpty()
            if ("@admin" in now) {
                groups = if (now.size == 1) {
                    groups - key
                } else {
                    groups + (key to (now - "@admin"))
                }
                returnReply("[red]{uuid} [green] has been removed from Admins[]".with("uuid" to uuid))
            } else {
                if (isQQ) {
                    groups = groups + (key to (now + "@admin"))
                    reply("[green]QQ [red]{qq}[] has been added to Admins".with("qq" to uuid))
                } else {
                    val info = netServer.admins.getInfoOptional(uuid)
                        ?: returnReply("[red]Can't found player".with())
                    groups = groups + (key to (now + "@admin"))
                    reply("[green]Player [red]{info.name}({info.uuid})[] has been added to Admins".with("info" to info))
                }
            }
        }
    }
}