# ACE'S ASSASSINS

A standalone Paper plugin project for a Minecraft assassination event.

## Requirements
- Paper 1.21.11 (or a compatible Paper build)
- Java 21
- Maven 3.9+

## Build
1. Open this folder in IntelliJ IDEA as a Maven project.
2. Let Maven import dependencies.
3. Run `mvn clean package` in the IntelliJ Maven terminal.
4. Copy `target/aces-assassins-1.0.0.jar` into the server's `plugins` folder.
5. Restart the server.

## Commands
- `/acesassassins start|end|cancel|status`
- `/acesassassins points`
- `/acesassassins shop`
- `/acesassassins rules`
- `/acesassassins config`
- `/acesassassins rule remove <id>`
- `/leaderboard`
- `/leaderboard place|remove|refresh` (admin)

## Permissions
- `acesassassins.admin` (default OP)
- `acesassassins.operator` (default OP)
- `acesassassins.play` (default true)

## Particle reference used
The supplied video shows a bright aqua/blue particle vortex and rising trail, with dense white sparkle particles and a central flash. The plugin's configurable `particleBurst` effect uses cyan/blue dust, end rods, cloud/firework particles and a flash to approximate that look. Tune it in `plugins/AcesAssassins/config.yml`.

## Important implementation notes
This is a first complete standalone project baseline, not a production-tested release. Test it on a staging server before using it in a live event. The plugin currently implements the core event lifecycle, circular target assignment, animated target reveal, compass tracking, regular and revenge scoring, player leaderboard GUI, voucher purchase/submission, Operator approval GUI, official rules GUI, basic physical text displays, winner reward, particle animation, and YAML persistence.

The physical display is currently a simple text display rather than a fully formatted multi-line ranking board. The config GUI is a starter toggle display; edit `config.yml` for the full set of particle and score values. Review and test death/respawn behavior with your exact Paper build before a live event.
