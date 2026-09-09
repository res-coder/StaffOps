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
