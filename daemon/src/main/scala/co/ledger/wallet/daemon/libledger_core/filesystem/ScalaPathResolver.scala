package co.ledger.wallet.daemon.libledger_core.filesystem

import co.ledger.core.PathResolver

class ScalaPathResolver extends PathResolver {
  /**
    * Resolves the path for a SQLite database file.
    *
    * @param path The path to resolve.
    * @return The resolved path.
    */
  override def resolveDatabasePath(path: String): String = ???

  /**
    * Resolves the path of a single log file.
    *
    * @param path The path to resolve.
    * @return The resolved path.
    */
  override def resolveLogFilePath(path: String): String = ???

  /**
    * Resolves the path for a json file.
    *
    * @param path The path to resolve.
    * @return The resolved path.
    */
  override def resolvePreferencesPath(path: String): String = ???
}
