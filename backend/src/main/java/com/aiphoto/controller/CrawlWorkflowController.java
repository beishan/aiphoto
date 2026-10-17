package com.aiphoto.controller;

import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlScanPage;
import com.aiphoto.repository.UserRepository;
import com.aiphoto.service.CrawlWorkflowService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlWorkflowController {
    private final CrawlWorkflowService workflow;
    private final UserRepository users;
    private final com.aiphoto.service.CrawlerExecutorService executors;
    private final com.aiphoto.service.CrawlService crawlService;

    @GetMapping("/queues")
    public List<com.aiphoto.service.CrawlerExecutorService.SiteQueueView> queues(Authentication auth) {
        return executors.siteQueues(owner(auth));
    }

    @PutMapping("/queues/{siteId}/executors")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void assign(@PathVariable Long siteId, @RequestBody List<Long> ids, Authentication auth) throws Exception {
        crawlService.getSite(siteId, owner(auth));
        executors.assignSiteExecutors(siteId, ids);
    }

    @GetMapping("/scan-pages")
    public List<CrawlScanPage> scans(Authentication auth) { return workflow.scans(owner(auth)); }

    @PostMapping("/scan-pages")
    public CrawlScanPage create(@RequestBody CrawlScanPage page, Authentication auth) {
        return workflow.saveScan(null, page, owner(auth));
    }

    @PutMapping("/scan-pages/{id}")
    public CrawlScanPage update(@PathVariable Long id, @RequestBody CrawlScanPage page, Authentication auth) {
        return workflow.saveScan(id, page, owner(auth));
    }

    @DeleteMapping("/scan-pages/{id}")
    public void delete(@PathVariable Long id, Authentication auth) { workflow.deleteScan(id, owner(auth)); }

    @PostMapping("/scan-pages/{id}/scan")
    public CrawlJob scan(@PathVariable Long id, Authentication auth) {
        return workflow.scan(id, owner(auth), false);
    }

    @GetMapping("/collection-pages")
    public Page<CrawlWorkflowService.DiscoveredPage> pages(Authentication auth,
            @RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "false") boolean pending,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return workflow.pages(owner(auth), siteId, query, pending,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(1000, size))));
    }

    @PostMapping("/collection-pages/{id}/collect")
    public CrawlJob collect(@PathVariable Long id, Authentication auth) {
        return workflow.collect(id, owner(auth));
    }

    @DeleteMapping("/collection-pages/{id}")
    public void deletePage(@PathVariable Long id, Authentication auth) {
        workflow.deleteDiscoveredPage(id, owner(auth));
    }

    @DeleteMapping("/collection-pages")
    public void deletePages(@RequestBody List<Long> ids, Authentication auth) {
        workflow.deleteDiscoveredPages(ids, owner(auth));
    }

    private Long owner(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow().getId();
    }
}
