package com.plantpal.identification.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/** Liquibase task: removes GPS from every stored photo referenced by scans or plants. */
public class StripPhotoGpsTask implements CustomTaskChange {

  private static final String PHOTO_URLS_SQL =
      "SELECT photo_url FROM identifications WHERE photo_url LIKE '/photos/%' "
          + "UNION SELECT photo_url FROM plants WHERE photo_url LIKE '/photos/%'";

  @Override
  public void execute(Database database) throws CustomChangeException {
    PhotoGpsCleaner cleaner = PhotoGpsCleaner.current();
    if (cleaner == null) {
      throw new CustomChangeException("Photo storage is not available to the GPS cleanup task");
    }
    try {
      run(((JdbcConnection) database.getConnection()).getUnderlyingConnection(), cleaner);
    } catch (SQLException e) {
      throw new CustomChangeException("Could not list stored photos", e);
    }
  }

  public static PhotoGpsCleaner.Summary run(Connection connection, PhotoGpsCleaner cleaner)
      throws SQLException {
    Set<String> urls = new LinkedHashSet<>();
    try (Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(PHOTO_URLS_SQL)) {
      while (rs.next()) {
        urls.add(rs.getString(1));
      }
    }
    return cleaner.cleanAll(urls);
  }

  @Override
  public String getConfirmationMessage() {
    return "Stored photos stripped of GPS metadata";
  }

  @Override
  public void setUp() {
    // no parameters
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    // not needed
  }

  @Override
  public ValidationErrors validate(Database database) {
    return new ValidationErrors();
  }
}
