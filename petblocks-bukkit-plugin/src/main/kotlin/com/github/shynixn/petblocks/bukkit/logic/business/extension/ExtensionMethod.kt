@file:Suppress("UNCHECKED_CAST")

package com.github.shynixn.petblocks.bukkit.logic.business.extension

import com.github.shynixn.petblocks.api.PetBlocksApi
import com.github.shynixn.petblocks.api.business.enumeration.MaterialType
import com.github.shynixn.petblocks.api.business.enumeration.ParticleType
import com.github.shynixn.petblocks.api.business.enumeration.Permission
import com.github.shynixn.petblocks.api.business.enumeration.Version
import com.github.shynixn.petblocks.api.business.service.ItemService
import com.github.shynixn.petblocks.api.persistence.entity.Position
import com.github.shynixn.petblocks.core.logic.business.extension.translateChatColors
import com.github.shynixn.petblocks.core.logic.persistence.entity.PositionEntity
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.MemorySection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.util.Vector
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*

/**
 * Created by Shynixn 2018.
 * <p>
 * Version 1.2
 * <p>
 * MIT License
 * <p>
 * Copyright (c) 2018 by Shynixn
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
/**
 * Sets the item in the players arm.
 */
fun PlayerInventory.setItemStackInHand(itemStack: ItemStack?, offHand: Boolean = false) {
    val version = getServerVersion()

    if (version.isVersionSameOrGreaterThan(Version.VERSION_1_9_R1)) {
        val inventoryClazz = Class.forName("org.bukkit.inventory.PlayerInventory")

        if (offHand) {
            inventoryClazz.getDeclaredMethod("setItemInOffHand", ItemStack::class.java).invoke(this, itemStack)
        } else {
            inventoryClazz.getDeclaredMethod("setItemInMainHand", ItemStack::class.java).invoke(this, itemStack)
        }
    } else {
        Class.forName("org.bukkit.entity.HumanEntity").getDeclaredMethod("setItemInHand", ItemStack::class.java)
            .invoke(this.holder, itemStack)
    }
}

/**
 * Deserializes the configuraiton section path to a map.
 */
fun FileConfiguration.deserializeToMap(path: String): Map<String, Any?> {
    val section = getConfigurationSection(path).getValues(false)
    deserialize(section)
    return section
}

/**
 * Deserializes the given section.
 */
fun deserialize(section: MutableMap<String, Any?>) {
    section.keys.forEach { key ->
        if (section[key] is MemorySection) {
            val map = (section[key] as MemorySection).getValues(false)
            deserialize(map)
            section[key] = map
        }
    }
}

/**
 * Teleports the player via packets to keep his state in the world.
 */
fun Player.teleportUnsafe(location: Location) {
    val version = getServerVersion()
    val craftPlayer = Class.forName("org.bukkit.craftbukkit.VERSION.entity.CraftPlayer".replace("VERSION", version.bukkitId)).cast(player)
    val methodHandle = craftPlayer.javaClass.getDeclaredMethod("getHandle")
    val entityPlayer = methodHandle.invoke(craftPlayer)
    val entityClazz = Class.forName("net.minecraft.server.VERSION.Entity".replace("VERSION", version.bukkitId))

    val setPositionMethod = entityClazz
        .getDeclaredMethod("setPositionRotation", Double::class.java, Double::class.java, Double::class.java, Float::class.java, Float::class.java)

    setPositionMethod.invoke(entityPlayer, location.x, location.y, location.z, location.yaw, location.pitch)

    val packetTeleport = Class.forName("net.minecraft.server.VERSION.PacketPlayOutEntityTeleport".replace("VERSION", version.bukkitId))
        .getDeclaredConstructor(entityClazz).newInstance(entityPlayer)

    location.world.players.forEach { worldPlayer ->
        worldPlayer.sendPacket(packetTeleport)
    }
}

/**
 * Converts the [Position] to a BukkitVector.
 */
fun Position.toVector(): Vector {
    return Vector(this.x, this.y, this.z)
}

/**
 * Converts the [Location] to a Position.
 */
fun Location.toPosition(): Position {
    val position = PositionEntity()

    position.worldName = this.world.name
    position.x = this.x
    position.y = this.y
    position.z = this.z
    position.yaw = this.yaw.toDouble()
    position.pitch = this.pitch.toDouble()

    return position
}

/**
 * Gets the item in the players arm.
 */
fun PlayerInventory.getItemStackInHand(offHand: Boolean = false): Optional<ItemStack> {
    val version = getServerVersion()

    return if (version.isVersionSameOrGreaterThan(Version.VERSION_1_9_R1)) {
        val inventoryClazz = Class.forName("org.bukkit.inventory.PlayerInventory")

        if (offHand) {
            Optional.ofNullable(inventoryClazz.getDeclaredMethod("getItemInOffHand").invoke(this)) as Optional<ItemStack>
        } else {
            Optional.ofNullable(inventoryClazz.getDeclaredMethod("getItemInMainHand").invoke(this)) as Optional<ItemStack>
        }
    } else {
        Optional.ofNullable(
            Class.forName("org.bukkit.entity.HumanEntity").getDeclaredMethod("getItemInHand")
                .invoke(this.holder)
        ) as Optional<ItemStack>
    }
}

/**
 * Updates this inventory.
 */
fun PlayerInventory.updateInventory() {
    Player::class.java.getDeclaredMethod("updateInventory").invoke(this.holder as Player)
}

/**
 * Clears the inventory completely
 */
fun Inventory.clearCompletely() {
    clear()

    for (i in 0 until contents.size) {
        setItem(i, null)
    }
}

/**
 * Transforms the given object to a memory section map.
 */
fun Any.yamlMap(deep: Boolean = false): Map<String, Any> {
    if (this !is MemorySection) {
        throw IllegalArgumentException("This object is not a MemorySection!")
    }

    return this.getValues(deep)
}

/**
 * Sends the given [packet] to this player.
 */
@Throws(ClassNotFoundException::class, IllegalAccessException::class, NoSuchMethodException::class, InvocationTargetException::class, NoSuchFieldException::class)
fun Player.sendPacket(packet: Any) {
    val version = getServerVersion()
    val craftPlayer = Class.forName("org.bukkit.craftbukkit.VERSION.entity.CraftPlayer".replace("VERSION", version.bukkitId)).cast(player)
    val methodHandle = craftPlayer.javaClass.getDeclaredMethod("getHandle")
    val entityPlayer = methodHandle.invoke(craftPlayer)

    val field = Class.forName("net.minecraft.server.VERSION.EntityPlayer".replace("VERSION", version.bukkitId)).getDeclaredField("playerConnection")
    field.isAccessible = true
    val connection = field.get(entityPlayer)

    val sendMethod = connection.javaClass.getDeclaredMethod("sendPacket", packet.javaClass.interfaces[0])
    sendMethod.invoke(connection, packet)
}

/**
 * Sets the itemstack unbreakable.
 */
fun ItemStack.setUnbreakable(unbreakable: Boolean): ItemStack {
    val data = HashMap<String, Any>()
    data["Unbreakable"] = unbreakable
    setNBTTags(data)
    return this
}

/**
 * Sets nbt tags to the itemstack.
 */
fun ItemStack.setNBTTags(tags: Map<String, Any>) {
    val version = getServerVersion()
    val nmsCopyMethod =
        Class.forName("org.bukkit.craftbukkit.VERSION.inventory.CraftItemStack".replace("VERSION", version.bukkitId)).getDeclaredMethod("asNMSCopy", ItemStack::class.java)

    val nbtTagClass = Class.forName("net.minecraft.server.VERSION.NBTTagCompound".replace("VERSION", version.bukkitId))
    val nmsItemStackClass = Class.forName("net.minecraft.server.VERSION.ItemStack".replace("VERSION", version.bukkitId))
    val getNBTTag = nmsItemStackClass.getDeclaredMethod("getTag")
    val setNBTTag = nmsItemStackClass.getDeclaredMethod("setTag", nbtTagClass)
    val nmsItemStack = nmsCopyMethod.invoke(null, this)

    val nbtSetString = nbtTagClass.getDeclaredMethod("setString", String::class.java, String::class.java)
    val nbtSetBoolean = nbtTagClass.getDeclaredMethod("setBoolean", String::class.java, Boolean::class.javaPrimitiveType)
    val nbtSetInteger = nbtTagClass.getDeclaredMethod("setInt", String::class.java, Int::class.javaPrimitiveType)

    for (key in tags.keys) {
        val value = tags[key]
        var nbtTag = getNBTTag.invoke(nmsItemStack)

        if (nbtTag == null) {
            nbtTag = nbtTagClass.newInstance()
        }

        when (value) {
            is String -> {
                nbtSetString.invoke(nbtTag, key, value)
            }

            is Int -> {
                nbtSetInteger.invoke(nbtTag, key, value)
            }

            is Boolean -> {
                nbtSetBoolean.invoke(nbtTag, key, value)
            }
        }

        setNBTTag.invoke(nmsItemStack, nbtTag)
    }
}

/**
 * Sets the displayName of an itemstack.
 */
fun ItemStack.setDisplayName(displayName: String): ItemStack {
    val meta = itemMeta
    meta.displayName = displayName.translateChatColors()
    itemMeta = meta
    return this
}

/**
 * Gets the displayname of an item.
 */
fun ItemStack.getDisplayName(): String? {
    if (this.itemMeta != null) {
        if (this.itemMeta.displayName != null) {
            return itemMeta.displayName
        }
    }

    return null
}

/**
 * Checks if this player has got the given [permission].
 */
fun Player.hasPermission(permission: Permission, vararg placeholder: String): Boolean {
    for (s in permission.getPermission()) {
        var perm = s
        for (i in placeholder.indices) {
            val plc = "$$i"
            perm = perm.replace(plc, placeholder[i])
        }
        if (player.hasPermission(perm)) {
            return true
        }
    }
    return false
}

/**
 * Sets the itemstack lore.
 */
fun ItemStack.setLore(lore: List<String>): ItemStack {
    val meta = itemMeta
    val tmpLore = ArrayList<String>()

    lore.forEach { l ->
        tmpLore.add(l.translateChatColors())
    }

    meta.lore = tmpLore

    itemMeta = meta
    return this
}

/**
 * Tries to return the [ParticleType] from the given [name].
 */
fun String.toParticleType(): ParticleType {
    val version = getServerVersion()

    ParticleType.values().forEach { p ->
        if (p.gameId_18.equals(this, true) || p.gameId_113.equals(this, true) || p.name.equals(this, true)) {
            if (version.isVersionSameOrGreaterThan(p.sinceVersion)) {
                return p
            }
        }
    }

    throw IllegalArgumentException("ParticleType cannot be parsed from '" + this + "'.")
}

/**
 * Sets the skin of an itemstack.
 */
internal fun ItemStack.setSkin(skin: String) {
    val currentMeta = this.itemMeta as? SkullMeta ?: return

    var newSkin = skin

    if (newSkin.length > 32) {
        val cls = Class.forName("org.bukkit.craftbukkit.VERSION.inventory.CraftMetaSkull".replace("VERSION", getServerVersion().bukkitId))
        val real = cls.cast(currentMeta)
        val field = real.javaClass.getDeclaredField("profile")
        val newSkinProfile = GameProfile(UUID.randomUUID(), null)

        if (newSkin.contains("textures.minecraft.net")) {
            if (!newSkin.startsWith("http://")) {
                newSkin = "http://$newSkin"
            }

            newSkin = Base64Coder.encodeString("{textures:{SKIN:{url:\"$newSkin\"}}}")
        }

        newSkinProfile.properties.put("textures", Property("textures", newSkin))
        field.isAccessible = true
        field.set(real, newSkinProfile)
        itemMeta = SkullMeta::class.java.cast(real)
    } else if (skin.isNotEmpty()) {
        currentMeta.owner = skin
        itemMeta = currentMeta
    }
}

/**
 * Is the material the given type?
 */
fun Material.isMaterial(type: MaterialType): Boolean {
    val itemService = PetBlocksApi.resolve<ItemService>(ItemService::class.java)

    return this == itemService.getMaterialValue<Material>(type.MinecraftNumericId)
}

/**
 * Converts the given string to a material.
 */
fun String.toMaterial(): Material {
    return Cache.itemService.getMaterialValue(this)
}

/**
 * Converts the given int to a material.
 */
fun Int.toMaterial(): Material {
    return Cache.itemService.getMaterialValue(this)
}

/**
 * TypeName of the material.
 */
val MaterialType.type: Material
    get() {
        return Cache.itemService.getMaterialValue<Material>(this.MinecraftNumericId)
    }

/**
 * Gets the server version the plugin is running on.
 */
fun Any.getServerVersion(): Version {
    if (Bukkit.getServer() == null || Bukkit.getServer().javaClass.getPackage() == null) {
        return Version.VERSION_UNKNOWN
    }

    val version = Bukkit.getServer().javaClass.getPackage().name.replace(".", ",").split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[3]

    for (versionSupport in Version.values()) {
        if (versionSupport.bukkitId == version) {
            return versionSupport
        }
    }

    return Version.VERSION_UNKNOWN
}

/**
 * Removes the final modifier from this field to allow editing.
 */
fun Field.removeFinalModifier() {
    isAccessible = true
    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    modifiersField.setInt(this, this.modifiers and Modifier.FINAL.inv())
}

private object Cache {
    val itemService = PetBlocksApi.resolve<ItemService>(ItemService::class.java)
}
