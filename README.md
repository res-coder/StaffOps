# StaffOps 1.1.1

StaffOps is a professional staff operations, moderation, investigation, and server-management suite built for modern Paper servers.

## Target
- Minecraft / Paper: **1.21.8**
- Java: **21**
- Build: **Maven**
- Storage: **SQLite with HikariCP**
- Optional integrations: **Vault, LuckPerms**

## Major systems
- Advanced Staff Mode with protected staff inventory
- Vanish, freeze, follow, POV/spectate, random-player navigation
- Complete player inspection and intelligence profiles
- Punishment management for warnings, mutes, kicks, and bans
- Player reports with categories, priority, claiming, watching, dismissal, and escalation
- Persistent moderation cases and investigation timelines
- Evidence snapshots and recent activity tracking
- Player notes and audit logging
- Staff chat
- Server Operations Center with performance and database health information
- Maintenance, whitelist, announcements, and confirmed restart tools
- Granular staff permissions
- Optional Vault and LuckPerms integration
- Configurable MiniMessage-based messaging with non-italic StaffOps UI text

## Privacy and security
- Raw IP storage is disabled by default.
- IP correlation uses salted hashes by default.
- Sensitive commands can be configured so arguments are never retained in activity history.
- Inventory and ender-chest inspection is read-only and protected from player manipulation.
- Privileged actions are permission checked server-side and important actions are audit logged.
- The default IP hash salt in `config.yml` is intentionally a placeholder and should be replaced before production use.

## Build
Requirements:
- JDK 21
- Maven 3.9+

```bash
mvn clean package
```

Expected output:

```text
target/StaffOps-1.1.1.jar
```

Do not use Minecraft `/reload`. Stop the Paper server, replace the JAR, and start the server normally. Existing `staffops.db` data can remain in place; StaffOps performs in-place schema migrations.

## Main commands
- `/so` - StaffOps dashboard and administrative tools
- `/staff` - toggle Staff Mode
- `/vanish` - toggle staff vanish
- `/freeze <player>` - freeze or unfreeze a player
- `/inspect <player>` - open a player profile
- `/punish <player>` - open punishment management
- `/report <player>` - submit a player report
- `/reports` - staff report center
- `/case` / `/cases` - moderation case tools
- `/evidence` - investigation evidence tools
- `/operations` - Server Operations Center
- `/staffchat` - staff communication

## Design notes
StaffOps keeps server-side permission enforcement separate from GUI visibility. Persistent moderation data uses SQLite through HikariCP. Optional integrations do not prevent the plugin from loading when absent. Activity and evidence systems are designed around useful moderation context rather than video recording.

## Production validation
Before using on a live server:
1. Build with Java 21 and test on Paper 1.21.8.
2. Replace `settings.privacy.ip-hash-salt` with a long random secret.
3. Verify each staff rank only has the permissions it should have.
4. Test Staff Mode, vanish, freeze, inventory inspection, punishments, reports, cases, and evidence with both staff and normal player accounts.
5. Restart the server and confirm persistence/migrations.
6. Test optional Vault/LuckPerms integrations if installed.
