@file:Suppress("UNCHECKED_CAST")

package com.github.shynixn.petblocks.bukkit.logic.business.service

import com.github.shynixn.petblocks.api.business.annotation.Inject
import com.github.shynixn.petblocks.api.business.enumeration.ChatClickAction
import com.github.shynixn.petblocks.api.business.enumeration.ChatColor
import com.github.shynixn.petblocks.api.business.enumeration.ScriptAction
import com.github.shynixn.petblocks.api.business.service.*
import com.github.shynixn.petblocks.api.persistence.entity.*
import com.github.shynixn.petblocks.bukkit.logic.business.extension.*
import com.github.shynixn.petblocks.core.logic.business.extension.chatMessage
import com.github.shynixn.petblocks.core.logic.business.extension.sync
import com.github.shynixn.petblocks.core.logic.business.extension.translateChatColors
import com.github.shynixn.petblocks.core.logic.persistence.entity.GuiIconEntity
import com.github.shynixn.petblocks.core.logic.persistence.entity.GuiPlayerCacheEntity
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.ArrayList
import java.util.logging.Level

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
class GUIServiceImpl @Inject constructor(
    private val configurationService: ConfigurationService,
    private val plugin: Plugin,
    private val petActionService: PetActionService,
    private val scriptService: GUIScriptService,
    private val persistenceService: PersistencePetMetaService,
    private val itemService: ItemService,
    private val concurrencyService: ConcurrencyService,
    private val headDatabaseService: DependencyHeadDatabaseService
) : GUIService {

    private val clickProtection = ArrayList<Player>()

    private val pageCache = HashMap<Player, GuiPlayerCache>()

    private var collectedMinecraftHeadsMessage = chatMessage {
        text {
            configurationService.findValue<String>("messages.prefix") + "Pets collected by "
        }
        component {
            color(ChatColor.YELLOW) {
                text {
                    ">>Minecraft-Heads.com<<"
                }
            }
            clickAction {
                ChatClickAction.OPEN_URL to "http://minecraft-heads.com"
            }
            hover {
                text {
                    "Goto the Minecraft-Heads website!"
                }
            }
        }
    }

    /**
     * Closes the gui for the given [player]. Does nothing when the GUI is already closed.
     */
    override fun <P> close(player: P) {
        if (player !is Player) {
            throw IllegalArgumentException("Player has to be a BukkitPlayer!")
        }

        player.closeInventory()
    }

    /**
     * Clears all resources the given player has allocated from this service.
     */
    override fun <P> cleanResources(player: P) {
        if (player !is Player) {
            throw IllegalArgumentException("Player has to be a BukkitPlayer!")
        }

        if (pageCache.containsKey(player)) {
            pageCache.remove(player)
        }
    }

    /**
     * Returns if the given [inventory] matches the inventory of this service.
     */
    override fun <I> isGUIInventory(inventory: I): Boolean {
        if (inventory !is Inventory) {
            throw IllegalArgumentException("Inventory has to be a BukkitInventory!")
        }

        val holder = inventory.holder as? Player ?: return false

        return this.pageCache.containsKey(holder) && this.pageCache[holder]!!.getInventory<Inventory>() == inventory
    }

    /**
     * Opens the gui for the given [player]. Does nothing when the GUI is already open.
     */
    override fun <P> open(player: P, pageName: String?) {
        if (player !is Player) {
            throw IllegalArgumentException("Player has to be a BukkitPlayer!")
        }

        if (player.openInventory != null) {
            player.closeInventory()
        }

        var page = pageName

        if (page == null) {
            page = "gui.main"
        }

        headDatabaseService.clearResources(player)

        val guiTitle = configurationService.findValue<String>("messages.gui-title")
        val inventory = Bukkit.getServer().createInventory(player, 54, guiTitle)
        player.openInventory(inventory)

        pageCache[player] = GuiPlayerCacheEntity(page, inventory)
        renderPage(player, page)
    }

    /**
     * Executes actions when the given [player] clicks on an [item] at the given [relativeSlot].
     */
    override fun <P, I> clickInventoryItem(player: P, relativeSlot: Int, item: I) {
        if (player !is Player) {
            throw IllegalArgumentException("Player has to be a BukkitPlayer!")
        }

        if (item !is ItemStack) {
            throw IllegalArgumentException("Item has to be a BukkitItemStack!")
        }

        val optGuiItem = configurationService.findClickedGUIItem(pageCache[player]!!.path, item) ?: return

        if (optGuiItem.permission.isNotEmpty() && !player.hasPermission(optGuiItem.permission)) {
            if (!lockGui(player)) {
                player.sendMessage(configurationService.findValue<String>("messages.prefix") + configurationService.findValue<String>("messages.no-perms"))
            }

            return
        }

        if (pageCache.containsKey(player)) {
            persistenceService.getOrCreateFromPlayerUUID(player.uniqueId.toString()).thenAccept { petMeta ->
                var changes = false

                if (optGuiItem.targetSkin != null) {
                    val skin = optGuiItem.targetSkin!!

                    petMeta.skin.typeName = skin.typeName
                    petMeta.skin.dataValue = skin.dataValue
                    petMeta.skin.owner = skin.owner
                    petMeta.skin.unbreakable = skin.unbreakable

                    changes = true
                }

                for (aiBase in optGuiItem.removeAIs.toTypedArray()) {
                    petMeta.aiGoals.removeIf { a -> a.type == aiBase.type }
                    changes = true
                }

                for (aiBase in optGuiItem.addAIs.toTypedArray()) {
                    petMeta.aiGoals.add(aiBase)
                    changes = true
                }

                if (changes) {
                    persistenceService.save(petMeta)
                }
            }
        }

        if (optGuiItem.script == null) {
            return
        }

        val scriptResult = scriptService.executeScript(optGuiItem.script!!)

        if (scriptResult.action == ScriptAction.SCROLL_PAGE) {
            val result = scriptResult.valueContainer as Pair<Int, Int>
            pageCache[player]!!.offsetX += result.first
            pageCache[player]!!.offsetY += result.second

            renderPage(player, pageCache[player]!!.path)
        } else if (scriptResult.action == ScriptAction.CALL_PET) {
            petActionService.callPet(player)
            this.close(player)
        } else if (scriptResult.action == ScriptAction.CLOSE_GUI) {
            val page = pageCache[player]!!

            if (page.parent == null) {
                this.close(player)
            } else {
                pageCache[player] = page.parent!!
                renderPage(player, pageCache[player]!!.path)
            }
        } else if (scriptResult.action == ScriptAction.OPEN_PAGE) {
            val parent = pageCache[player]!!
            pageCache[player] = GuiPlayerCacheEntity(scriptResult.valueContainer as String, parent.getInventory())
            pageCache[player]!!.parent = parent
            renderPage(player, scriptResult.valueContainer as String)
        }
    }

    /**
     * Renders a single gui page.
     */
    private fun renderPage(player: Player, path: String) {
        player.openInventory.topInventory.clear()

        val items = configurationService.findGUIItemCollection(path)
        val inventory = player.openInventory.topInventory

        if (items == null) {
            plugin.logger.log(Level.WARNING, "Failed to load gui path '$path'.")
            return
        }

        fillEmptySlots(inventory)

        persistenceService.getOrCreateFromPlayerUUID(player.uniqueId.toString()).thenAccept { petMeta ->
            for (item in items) {
                if (item.hidden) {
                    continue
                }

                if (petMeta.enabled && item.hiddenWhenPetIsSpawned) {
                    continue
                }

                var hasPermission = true

                if (item.permission.isNotEmpty()) {
                    hasPermission = player.hasPermission(item.permission)
                }

                if (!hasPermission && item.hiddenWhenNoPermission) {
                    continue
                }

                val position = if (item.fixed) {
                    item.position
                } else {
                    scrollCollection(player, item.position)
                }

                if (position < 0 || position > 53) {
                    continue
                }

                if (item.icon.script != null) {
                    val scriptResult = scriptService.executeScript(item.icon.script!!)

                    if (scriptResult.action == ScriptAction.COPY_PET_SKIN) {
                        val guiIcon = GuiIconEntity()
                        guiIcon.displayName = petMeta.displayName

                        with(guiIcon.skin) {
                            typeName = petMeta.skin.typeName
                            dataValue = petMeta.skin.dataValue
                            owner = petMeta.skin.owner
                            unbreakable = petMeta.skin.unbreakable
                        }

                        renderIcon(inventory, position, guiIcon, hasPermission)
                    } else if (scriptResult.action == ScriptAction.HIDE_RIGHT_SCROLL && item.script != null) {
                        val itemPreScriptResult = scriptService.executeScript(item.script!!)
                        val offsetData = itemPreScriptResult.valueContainer as Pair<Int, Int>

                        val cachedData = Pair(pageCache[player]!!.offsetX, pageCache[player]!!.offsetY)
                        pageCache[player]!!.offsetX += offsetData.first

                        var found = false

                        if (offsetData.first > 0) {
                            for (s in items) {
                                if (s.hidden) {
                                    continue
                                }

                                if (petMeta.enabled && s.hiddenWhenPetIsSpawned) {
                                    continue
                                }

                                val pos = scrollCollection(player, s.position)

                                if (pos in 0..53) {
                                    found = true
                                }
                            }
                        }

                        pageCache[player]!!.offsetX = cachedData.first
                        pageCache[player]!!.offsetY = cachedData.second

                        if (found) {
                            renderIcon(inventory, position, item.icon, hasPermission)
                        }
                    } else if (scriptResult.action == ScriptAction.HIDE_LEFT_SCROLL && item.script != null) {
                        val itemPreScriptResult = scriptService.executeScript(item.script!!)
                        val offsetData = itemPreScriptResult.valueContainer as Pair<Int, Int>

                        if (offsetData.first < 0) {
                            if (pageCache[player]!!.offsetX > 0) {
                                renderIcon(inventory, position, item.icon, hasPermission)
                            }

                            continue
                        }
                    }
                } else {
                    renderIcon(inventory, position, item.icon, hasPermission)
                }
            }

            this.fillEmptySlots(inventory)
            player.inventory.updateInventory()
        }
    }

    /**
     * Renders a gui Icon.
     */
    private fun renderIcon(inventory: Inventory, position: Int, guiIcon: GuiIcon, hasPermission: Boolean) {
        if (position < 0) {
            return
        }

        val permissionMessage = if (hasPermission) {
            configurationService.findValue("messages.perms-ico-yes")
        } else {
            configurationService.findValue<String>("messages.perms-ico-no")
        }

        val itemStack = itemService.createItemStack<ItemStack>(guiIcon.skin.typeName, guiIcon.skin.dataValue)

        itemStack.setDisplayName(guiIcon.displayName)
        itemStack.setSkin(guiIcon.skin.owner)
        itemStack.setUnbreakable(guiIcon.skin.unbreakable)

        val meta = itemStack.itemMeta
        val tmpLore = ArrayList<String>()

        guiIcon.lore.forEach { l ->
            tmpLore.add(l.replace("<permission>", permissionMessage).translateChatColors())
        }

        meta.lore = tmpLore
        itemStack.itemMeta = meta

        inventory.setItem(position, itemStack)
    }

    /**
     * Fills up the given [inventory] with the default item.
     */
    private fun fillEmptySlots(inventory: Inventory) {
        val guiItem = this.configurationService.findGUIItemCollection("static-gui")!![0]

        for (i in 0 until inventory.contents.size) {
            if (inventory.getItem(i) == null || inventory.getItem(i).type == Material.AIR) {
                renderIcon(inventory, i, guiItem.icon, true)
            }
        }
    }

    /**
     * Locks the GUI against spamming.
     * Returns if locked.
     */
    private fun lockGui(player: Player): Boolean {
        if (clickProtection.contains(player)) {
            return true
        }

        this.clickProtection.add(player)

        sync(concurrencyService, 10L) {
            this.clickProtection.remove(player)
        }

        return false
    }

    /**
     * Scrolls the page to the axes.
     */
    private fun scrollCollection(player: Player, sourcePosition: Int): Int {
        val vPosition = sourcePosition % 54
        val multiplier = sourcePosition / 54

        val offsetX = pageCache[player]!!.offsetX
        val row = vPosition / 9
        var column = (vPosition % 9) + multiplier * 9

        column -= offsetX

        if (column < 0 || column > 8) {
            return -1
        }

        return row * 9 + column
    }
}