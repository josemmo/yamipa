import { placeBlockOnTheFloor, startBot } from './src/bot.js'
import { wait } from './src/common.js'
import { getRconClient, waitForServer } from './src/rcon.js'

(async() => {
    console.log('[AUTOMATA] Waiting for Minecraft server to be ready...')
    await waitForServer()

    console.log('[AUTOMATA] Logging in as test account...')
    const bot = await startBot()
    await bot.look(0, -Math.PI/2)

    console.log('[AUTOMATA] Making test account an OP...')
    const conn = await getRconClient()
    await conn.send('op test')
    await conn.send('gamemode creative test')
    await wait(2000)

    console.log('[AUTOMATA] Preparing test account...')
    bot.chat('/clear @p')
    await wait(2000)
    bot.chat('/give @p minecraft:dirt')
    await wait(2000)

    console.log('[AUTOMATA] Placing image on the floor...')
    bot.chat('/image place pic-1.jpg 4 4')
    await wait(2000)
    placeBlockOnTheFloor(bot)
    await wait(2000)

    console.log('[AUTOMATA] Removing image from floor...')
    bot.chat('/image remove')
    await wait(2000)
    placeBlockOnTheFloor(bot)
    await wait(2000)

    console.log('[AUTOMATA] Logging off...')
    bot.end()

    console.log('[AUTOMATA] Stopping server...')
    await conn.send('stop')
    await conn.end()

    console.log('[AUTOMATA] Done!')
})()
