package likelion.flourishing.domain.report.repository;

import java.util.List;
import likelion.flourishing.domain.report.entity.GuideSectionCopy;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideSectionCopyRepository extends JpaRepository<GuideSectionCopy, GuideSectionKey> {

    List<GuideSectionCopy> findAllByOrderByDisplayOrderAsc();
}
