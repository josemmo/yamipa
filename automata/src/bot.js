import mineflayer from 'mineflayer'
import { Vec3 } from 'vec3'

/**
 * @return {Promise<mineflayer.Bot>} Bot instance
 */
export function startBot() {
    return new Promise(resolve => {
        const bot = mineflayer.createBot({
            host: '127.0.0.1',
            username: 'test',
        })
        bot.once('spawn', () => resolve(bot))
    })
}

/**
 * @param {mineflayer.Bot} bot Bot instance
 */
export async function clickBlockOnTheFloor(bot) {
    const targetBlock = bot.blockAt(bot.entity.position.offset(0, -1, 0))
    await bot.activateBlock(targetBlock, null, new Vec3(0, 1, 0))
}
