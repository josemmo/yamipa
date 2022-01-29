import mineflayer from 'mineflayer'

/**
 * @return {Promise<mineflayer.Bot>} Bot instance
 */
export function startBot() {
    return new Promise(resolve => {
        const bot = mineflayer.createBot({
            host: '127.0.0.1',
            username: 'test'
        })
        bot.once('spawn', () => resolve(bot))
    })
}

/**
 * @param {mineflayer.Bot} bot Bot instance
 */
export function placeBlockOnTheFloor(bot) {
    bot._client.write('block_place', {
        location: bot.entity.position.offset(0, -1, 0),
        direction: 1,
        hand: 0,
        cursorX: 0,
        cursorY: 0,
        cursorZ: 0
    })
}
