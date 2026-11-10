package menupick.service;

import menupick.config.MenuPickProperties;
import menupick.domain.Inquiry;
import menupick.repository.MenuPickRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class InquiryService {
    private final MenuPickRepository repository;
    private final String adminEmail;

    public InquiryService(MenuPickRepository repository, MenuPickProperties properties) {
        this.repository = repository;
        this.adminEmail = properties.adminEmail();
    }

    public Inquiry submit(String message) {
        return repository.saveInquiry(message, adminEmail);
    }

    public Optional<Inquiry> latest() {
        return repository.findLatestInquiry();
    }

    public List<Inquiry> recent(int limit) {
        return repository.findRecentInquiries(limit);
    }

    public int delete(Collection<Long> ids) {
        return repository.deleteInquiries(ids);
    }
}
