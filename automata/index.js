import { placeBlockOnTheFloor, startBot } from './src/bot.js'
import { wait } from './src/common.js'
import { getRconClient, waitForServer } from './src/rcon.js'

(async() => {
    console.log('Waiting for Minecraft server to be ready...')
    await waitForServer()

    console.log('Making test account an OP...')
    const conn = await getRconClient()
    await conn.send('op @p')
    await conn.send('gamemode creative @p')
    await conn.end()

    console.log('Logging in as test account...')
    const bot = await startBot()
    await bot.look(0, -Math.PI/2)
    bot.chat('/give @p minecraft:dirt')

    console.log('Placing image on the floor...')
    bot.chat('/image place pic-1.jpg 4 4')
    await wait(2000)
    placeBlockOnTheFloor(bot)
    await wait(2000)

    console.log('Removing image from floor...')
    bot.chat('/image remove')
    await wait(2000)
    placeBlockOnTheFloor(bot)
    await wait(2000)

    console.log('Logging off...')
    bot.end()
    console.log('Done!')
})()
