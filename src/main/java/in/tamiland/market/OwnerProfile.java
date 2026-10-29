package in.tamiland.market;

import java.util.List;

public record OwnerProfile(String email, String fullName, String phone, int activeListings, List<Plot> listings) {}
