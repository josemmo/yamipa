import { Rcon } from 'rcon-client'
import { wait } from './common.js'

const CONNECTION = {
    host: '127.0.0.1',
    port: 25575,
    password: 'rcon',
    timeout: 1000*20,
}

export async function waitForServer() {
    while (true) {
        try {
            const rcon = await Rcon.connect(CONNECTION)
            await rcon.end()
            break
        } catch (_) {
            // Not ready yet
        }
        wait(2000)
    }
}

export function getRconClient() {
    return Rcon.connect(CONNECTION)
}
