package com.github.shynixn.petblocks.core.logic.business.service

import com.github.shynixn.petblocks.api.business.enumeration.ChatColor
import com.github.shynixn.petblocks.api.business.service.*
import com.github.shynixn.petblocks.core.logic.business.extension.sync
import com.github.shynixn.petblocks.core.logic.business.extension.translateChatColors
import com.github.shynixn.petblocks.core.logic.persistence.entity.ChatMessageEntity
import com.google.inject.Inject

/**
 * Created by Shynixn 2019.
 * <p>
 * Version 1.2
 * <p>
 * MIT License
 * <p>
 * Copyright (c) 2019 by Shynixn
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
class PetDebugServiceImpl @Inject constructor(
    private val proxyService: ProxyService,
    concurrencyService: ConcurrencyService,
    private val yamlSerializationService: YamlSerializationService,
    private val yamlConfigurationService: YamlConfigurationService,
    private val messageService: MessageService,
    private val configurationService: ConfigurationService,
    private val petMetaService: PersistencePetMetaService
) : PetDebugService, Runnable {
    private val registeredSources = HashMap<Any, Any>()

    /**
     * Init.
     */
    init {
        sync(concurrencyService, 0L, 20L) {
            if (!registeredSources.isEmpty()) {
                this.run()
            }
        }
    }

    /**
     * Gets if the given [source] is registered.
     */
    override fun <P> isRegistered(source: P): Boolean {
        return registeredSources.containsKey(source as Any)
    }

    /**
     * Registers the given [source] to get notified by the given player pet.
     */
    override fun <P> register(source: P, player: P) {
        if (!registeredSources.containsKey(source as Any)) {
            registeredSources[source] = player as Any
        }
    }

    /**
     * Unregister the given [source].
     */
    override fun <S> unRegister(source: S) {
        if (registeredSources.containsKey(source as Any)) {
            registeredSources.remove(source)
        }
    }

    /**
     * When an object implementing interface `Runnable` is used
     * to create a thread, starting the thread causes the object's
     * `run` method to be called in that separately executing
     * thread.
     *
     *
     * The general contract of the method `run` is that it may
     * take any action whatsoever.
     *
     * @see java.lang.Thread.run
     */
    override fun run() {
        val prefix = configurationService.findValue<String>("messages.prefix")

        for (source in registeredSources.keys) {
            for (i in 0..19) {
                messageService.sendSourceMessage(source, "", false)
            }

            messageService.sendSourceMessage(source, "", false)
            messageService.sendSourceMessage(source,
                ChatColor.DARK_GREEN.toString() + "" + ChatColor.BOLD + ChatColor.UNDERLINE + "                   PetBlocks " + "                       ",
                false)

            messageService.sendSourceMessage(source, "", false)
            messageService.sendSourceMessage(source, ChatColor.DARK_GREEN.toString() + "" + ChatColor.ITALIC + "Move your mouse over the text! Disable debug mode via: /petblocks debug ", false)
            messageService.sendSourceMessage(source, "", false)

            val playerProxy = proxyService.findPlayerProxyObject(registeredSources[source])
            val petMeta = petMetaService.getPetMetaFromPlayer(playerProxy)

            val internalMessage = ChatMessageEntity()
                .appendComponent()
                .append(prefix).append("Player: ${playerProxy.name} ").appendHoverComponent().append("UUID: " + playerProxy.uniqueId).getRoot().append("\n")
                .appendComponent()
                .append(prefix).append("Petname: ${petMeta.displayName.translateChatColors()}").append("\n")
                .appendComponent()
                .append(prefix).append("Enabled: ${petMeta.enabled}").append("\n")
                .appendComponent()
                .append(prefix).append("Skin: [Type: ${petMeta.skin.typeName} Data: ${petMeta.skin.dataValue} Unbreakable: ${petMeta.skin.unbreakable}]").appendHoverComponent().append("Skin: " + petMeta.skin.owner).getRoot().append("\n")
                .append(prefix).append("Sound: ${petMeta.soundEnabled} Particles: ${petMeta.particleEnabled}").append("\n")
                .append(prefix).append("AI: ").append("\n")

            for(ai in petMeta.aiGoals){
                val serialized = yamlSerializationService.serialize(ai)
                val data = yamlConfigurationService.serializeToString("ai", serialized)

                internalMessage.appendComponent().append(prefix).append("- ${ai.type}").appendHoverComponent().append(data).getRoot().append("\n")
            }

            this.messageService.sendPlayerMessage(source, internalMessage)

            messageService.sendSourceMessage(source,
                ChatColor.DARK_GREEN.toString() + "" + ChatColor.BOLD + "" + ChatColor.UNDERLINE + "                        ┌Debug┐                      ", false)
            messageService.sendSourceMessage(source, "", false)
        }
    }
}