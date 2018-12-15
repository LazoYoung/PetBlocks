@file:Suppress("UNCHECKED_CAST")

package com.github.shynixn.petblocks.bukkit.logic.business.service

import com.github.shynixn.petblocks.api.business.annotation.Inject
import com.github.shynixn.petblocks.api.business.enumeration.ChatClickAction
import com.github.shynixn.petblocks.api.business.service.ConfigurationService
import com.github.shynixn.petblocks.api.business.service.ItemService
import com.github.shynixn.petblocks.api.persistence.entity.ChatMessage
import com.github.shynixn.petblocks.api.persistence.entity.GuiItem
import com.github.shynixn.petblocks.api.persistence.entity.PetMeta
import com.github.shynixn.petblocks.bukkit.logic.business.extension.deserializeToMap
import com.github.shynixn.petblocks.bukkit.logic.business.extension.toParticleType
import com.github.shynixn.petblocks.core.logic.business.extension.chatMessage
import com.github.shynixn.petblocks.core.logic.business.extension.getNullableItem
import com.github.shynixn.petblocks.core.logic.business.extension.translateChatColors
import com.github.shynixn.petblocks.core.logic.persistence.entity.*
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.MemorySection
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
class ConfigurationServiceImpl @Inject constructor(private val plugin: Plugin, private val itemService: ItemService) : ConfigurationService {
    private val cache = HashMap<String, List<GuiItem>>()
    private var namingMessage: ChatMessage? = null
    private var skullNamingMessage: ChatMessage? = null

    /**
     * Tries to load the config value from the given [path].
     * Throws a [IllegalArgumentException] if the path could not be correctly
     * loaded.
     */
    override fun <C> findValue(path: String): C {
        if (path == "messages.naming-suggest") {
            if (namingMessage == null) {
                namingMessage = chatMessage {
                    text {
                        findValue<String>("messages.prefix") + findValue("messages.naming-suggest-prefix")
                    }
                    component {
                        text {
                            findValue("messages.naming-suggest-clickable")
                        }
                        clickAction {
                            ChatClickAction.SUGGEST_COMMAND to "/" + findValue("petblocks-gui.command") + " rename "
                        }
                        hover {
                            text {
                                findValue("messages.naming-suggest-hover")
                            }
                        }
                    }
                    text {
                        findValue("messages.naming-suggest-suffix")
                    }
                }
            }

            return namingMessage as C
        }

        if (path == "messages.skullnaming-suggest") {
            if (skullNamingMessage == null) {
                skullNamingMessage = chatMessage {
                    text {
                        findValue<String>("messages.prefix") + findValue("messages.skullnaming-suggest-prefix")
                    }
                    component {
                        text {
                            findValue("messages.skullnaming-suggest-clickable")
                        }
                        clickAction {
                            ChatClickAction.SUGGEST_COMMAND to "/" + findValue("petblocks-gui.command") + " skin "
                        }
                        hover {
                            text {
                                findValue("messages.skullnaming-suggest-hover")
                            }
                        }
                    }
                    text {
                        findValue("messages.skullnaming-suggest-suffix")
                    }
                }
            }

            return skullNamingMessage as C
        }

        if (path == "plugin.version") {
            return plugin.description.version as C
        }

        if (!plugin.config.contains(path)) {
            throw IllegalArgumentException("Path '$path' could not be found!")
        }

        var data = this.plugin.config.get(path)

        if (data is String) {
            data = ChatColor.translateAlternateColorCodes('&', data)
            return data as C
        }

        if (data is MemorySection) {
            if (data.contains("name") && data.contains("speed") && data.contains("amount")) {
                val particle = ParticleEntity()
                val values = data.getValues(false)

                with(particle) {
                    type = (values["name"] as String).toParticleType()
                    amount = values["amount"] as Int
                    speed = values["speed"] as Double
                }

                if (values.containsKey("offx")) {
                    with(particle) {
                        offSetX = values["offx"] as Double
                        offSetY = values["offy"] as Double
                        offSetZ = values["offz"] as Double
                    }
                }

                if (values.containsKey("red")) {
                    with(particle) {
                        colorRed = values["red"] as Int
                        colorGreen = values["green"] as Int
                        colorBlue = values["blue"] as Int
                    }
                }


                if (values.containsKey("id")) {
                    if (values["id"] is String) {
                        particle.materialName = values["id"] as String
                    } else {
                        throw IllegalArgumentException("WAT")
                        //  particle.materialName = MaterialCompatibility13.getMaterialFromId(values["id"] as Int).name
                    }
                }

                return particle as C
            } else if (data.contains("name") && data.contains("volume")) {
                val sound = SoundEntity()
                val values = data.getValues(false)

                with(sound) {
                    name = values["name"] as String
                    volume = values["volume"] as Double
                    pitch = values["pitch"] as Double
                }

                return sound as C
            }

            return plugin.config.deserializeToMap(path) as C
        }

        return data as C
    }

    /**
     * Tries to return a list of [GuiItem] matching the given path from the config.
     * Can be called asynchronly.
     */
    override fun findGUIItemCollection(path: String): List<GuiItem>? {
        if (cache.containsKey(path)) {
            return cache[path]!!
        }

        val items = ArrayList<GuiItem>()
        val section = (plugin.config.get(path) as MemorySection).getValues(false)

        section.keys.forEach { key ->
            val guiItem = GuiItemEntity()
            val guiIcon = guiItem.icon
            val description = (section[key] as MemorySection).getValues(true)

            if (description.containsKey("row") && description.containsKey("col")) {
                var column = (description["col"] as Int - 1)
                column += ((column / 9) * 45)
                guiItem.position = (description["row"] as Int - 1) * 9 + column
            }

            this.setItem<String>("permission", description) { value -> guiItem.permission = value }
            this.setItem<Boolean>("hidden-when-no-permission", description) { value -> guiItem.hiddenWhenNoPermission = value }
            this.setItem<Boolean>("hidden", description) { value -> guiItem.hidden = value }
            this.setItem<Boolean>("hidden-when-pet-is-spawned", description) { value -> guiItem.hiddenWhenPetIsSpawned = value }
            this.setItem<Int>("position", description) { value -> guiItem.position = value - 1 }
            this.setItem<Boolean>("fixed", description) { value -> guiItem.fixed = value }
            this.setItem<String>("script", description) { value -> guiItem.script = value }

            this.setItem<Int>("icon.id", description) { value -> guiIcon.skin.typeName = itemService.getMaterialFromNumericValue<Material>(value).name }
            this.setItem<Int>("icon.damage", description) { value -> guiIcon.skin.dataValue = value }
            this.setItem<String>("icon.name", description) { value -> guiIcon.displayName = value }
            this.setItem<Boolean>("icon.unbreakable", description) { value -> guiIcon.skin.unbreakable = value }
            this.setItem<String>("icon.skin", description) { value -> guiIcon.skin.owner = value }
            this.setItem<String>("icon.script", description) { value -> guiIcon.script = value }
            this.setItem<List<String>>("icon.lore", description) { value -> guiIcon.lore = value }

            if (guiItem.icon.displayName.startsWith("minecraft-heads.com/")) {
                guiItem.icon.displayName = findMinecraftHeadsItem(guiItem.icon.displayName.split("/")[1].toInt()).first
            }

            if (guiItem.icon.skin.owner.startsWith("minecraft-heads.com/")) {
                guiItem.icon.skin.owner = findMinecraftHeadsItem(guiItem.icon.skin.owner.split("/")[1].toInt()).second
            }

            items.add(guiItem)
        }

        cache[path] = items
        return items
    }

    /**
     * Sets optional gui items to a instance.
     */
    private fun <T> setItem(key: String, map: Map<String, Any>, f: (T) -> Unit) {
        if (map.containsKey(key)) {
            f.invoke(map[key]!! as T)
        }
    }

    /**
     * Tries to return a [GuiItem] matching the displayName and the lore of the given [item].
     * Can be called from Asynchronly.
     */
    override fun <I> findClickedGUIItem(path: String, item: I): GuiItem? {
        if (item !is ItemStack) {
            throw IllegalArgumentException("Item has to be an BukkitItemStack")
        }

        if (!this.cache.containsKey(path)) {
            return null
        }

        if (item.itemMeta == null || item.itemMeta.displayName == null) {
            return null
        }

        this.cache[path]!!.forEach { guiItem ->
            try {
                if (item.itemMeta.displayName == guiItem.icon.displayName.translateChatColors()) {
                    if ((item.itemMeta.lore == null && guiItem.icon.lore.isEmpty()) || (item.itemMeta.lore.size == guiItem.icon.lore.size)) {
                        return guiItem
                    }
                }
            } catch (e: Exception) {
                // Ignored
            }
        }

        return null
    }

    /**
     * Clears cached resources and refreshes the used configuration.
     */
    override fun refresh() {
        cache.clear()
        plugin.reloadConfig()
        namingMessage = null
        skullNamingMessage = null
    }

    /**
     * Generates the default pet meta.
     */
    override fun generateDefaultPetMeta(uuid: String, name: String): PetMeta {
        val playerMeta = PlayerMetaEntity(uuid, name)
        val petMeta = PetMetaEntity(playerMeta, SkinEntity())

        val defaultConfig = findValue<Map<String, Any?>>("pet")
        val skin = defaultConfig["skin"] as Map<String, Any?>
        val modifier = defaultConfig["modifier"] as Map<String, Any?>

        with(petMeta) {
            enabled = defaultConfig.getNullableItem("enabled")
            displayName = defaultConfig.getNullableItem<String>("name").replace("<player>", name)
            soundEnabled = defaultConfig.getNullableItem("sound-enabled")
            particleEnabled = defaultConfig.getNullableItem("particle-enabled")
        }

        val typePayload = skin["typename"]

        val typename = if (typePayload is Int) {
            itemService.getMaterialFromNumericValue<Material>(typePayload).name
        } else {
            typePayload as String
        }

        with(petMeta.skin) {
            typeName = typename
            dataValue = skin.getNullableItem("datavalue")
            unbreakable = skin.getNullableItem("unbreakable")
            owner = skin.getNullableItem("owner")
        }

        val goalsMap = defaultConfig["goals"] as Map<String, Any?>

        goalsMap.keys.forEach { key ->
            val goal = goalsMap[key] as Map<String, Any?>

            if (goal["id"] == "hopping") {
                //    petMeta.aiGoals.add(AIHoppingEntity())
            }
        }

        return petMeta
    }

    /**
     * Tries to find a minecraft heads.com pair.
     */
    private fun findMinecraftHeadsItem(id: Int): Pair<String, String> {
        val identifier = id.toString()
        val decipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")

        decipher.init(Cipher.DECRYPT_MODE,
            SecretKeySpec(Base64.getDecoder().decode("NjI1MDE0YWUzMDkxNDQzNA=="), "AES"),
            IvParameterSpec("RandomInitVector".toByteArray(charset("UTF-8"))))
        BufferedReader(InputStreamReader(CipherInputStream(plugin.getResource("assets/petblocks/minecraftheads.db"), decipher))).use { reader ->
            while (true) {
                val s = reader.readLine() ?: break

                if (s.startsWith(identifier)) {
                    val content = s.split(";")
                    return Pair(content[2], content[3])
                }
            }
        }

        throw RuntimeException("Cannot locate minecraft-heads.com item with id $id.")
    }
}