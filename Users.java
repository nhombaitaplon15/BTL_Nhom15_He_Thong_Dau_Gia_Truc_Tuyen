abstract class Users extends Entity {
    private String ID;
    private String accountNumber;
    private String phoneNumber;
    private String email;
    public String getID() {
        return ID;
    }
    public String getAccountNumber() {
        return accountNumber;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public String getEmail() {
        return email;
    }
}
