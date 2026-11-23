package com.sica.domain.port.out;
import com.sica.domain.AccessPolicy;
import com.sica.domain.port.out.Store.*;
import java.time.LocalDateTime;
import java.util.List;
public interface DirectoryRepository {
    List<Option> companies(Long company);
    boolean activeCompany(long id);
    Person lockPerson(long id);
    long company(Long id, Company form);
    long person(Long id, PersonForm form);
    long user(Long id, UserForm form);
    void archive(Entity entity, long id);
}
