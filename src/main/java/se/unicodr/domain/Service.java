package se.unicodr.domain;

public class Service {

  private String id;
  private String name;
  private String url;
  private String status;
  private String lastCheck;

  public Service() {
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getLastCheck() {
    return lastCheck;
  }

  public void setLastCheck(String lastCheck) {
    this.lastCheck = lastCheck;
  }
}
