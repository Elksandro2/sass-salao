package com.cristiane.salon.models.service.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.repository.AppointmentServiceItemRepository;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.models.service.dto.SalonServiceFilter;
import com.cristiane.salon.models.service.dto.SalonServiceRequest;
import com.cristiane.salon.models.service.dto.SalonServiceResponse;
import com.cristiane.salon.models.service.dto.ServiceProductUsageRequest;
import com.cristiane.salon.models.service.dto.ServiceProductUsageResponse;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.entity.SalonServiceProductUsage;
import com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository;
import com.cristiane.salon.models.service.repository.SalonServiceRepository;
import com.cristiane.salon.models.service.specification.SalonServiceSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalonServiceManager {

    private final SalonServiceRepository salonServiceRepository;
    private final SalonServiceProductUsageRepository serviceProductUsageRepository;
    private final ProductRepository productRepository;
    private final AppointmentServiceItemRepository appointmentServiceItemRepository;

    private List<ServiceProductUsageResponse> loadUsages(Long serviceId) {
        return serviceProductUsageRepository.findBySalonServiceId(serviceId).stream()
                .map(ServiceProductUsageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SalonServiceResponse> findAll(SalonServiceFilter filter, Pageable pageable) {
        return salonServiceRepository.findAll(SalonServiceSpecifications.filter(filter), pageable)
                .map(service -> SalonServiceResponse.fromEntity(service, loadUsages(service.getId())));
    }

    @Transactional(readOnly = true)
    public SalonServiceResponse findById(Long id) {
        SalonService service = salonServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado"));
        return SalonServiceResponse.fromEntity(service, loadUsages(id));
    }

    @Transactional
    public SalonServiceResponse create(SalonServiceRequest request) {
        SalonService service = new SalonService();
        service.setName(request.name());
        service.setDescription(request.description());
        if (request.price() != null && request.price().signum() < 0) {
            throw new BadRequestException("O preço não pode ser negativo");
        }
        service.setPrice(request.price());
        service.setActive(request.active() != null ? request.active() : true);
        service.setCommissionPercent(request.commissionPercent());

        SalonService saved = salonServiceRepository.save(service);
        List<ServiceProductUsageResponse> usages = replaceProductUsages(saved, request.productUsages());

        return SalonServiceResponse.fromEntity(saved, usages);
    }

    @Transactional
    public SalonServiceResponse update(Long id, SalonServiceRequest request) {
        SalonService service = salonServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado"));

        if (request.price() != null && request.price().signum() < 0) {
            throw new BadRequestException("O preço não pode ser negativo");
        }
        if (request.name() != null) service.setName(request.name());
        if (request.description() != null) service.setDescription(request.description());
        service.setPrice(request.price());
        if (request.active() != null) service.setActive(request.active());
        service.setCommissionPercent(request.commissionPercent());

        SalonService saved = salonServiceRepository.save(service);
        List<ServiceProductUsageResponse> usages = replaceProductUsages(saved, request.productUsages());

        return SalonServiceResponse.fromEntity(saved, usages);
    }

    /** null = não mexe na receita atual (ex.: update parcial); lista (mesmo vazia) = substitui por completo. */
    private List<ServiceProductUsageResponse> replaceProductUsages(SalonService service, List<ServiceProductUsageRequest> requests) {
        if (requests == null) {
            return loadUsages(service.getId());
        }

        serviceProductUsageRepository.deleteBySalonServiceId(service.getId());
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }

        List<SalonServiceProductUsage> toSave = requests.stream().map(r -> {
            Product product = productRepository.findById(r.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

            // A unidade da receita pode ser diferente da do produto (ex.: produto embalado em
            // Litro, receita consome em ml) — só não pode ser de outra grandeza (ml pra g não
            // faz sentido). Sem unidade em nenhum dos dois lados, não há o que validar.
            if (r.unit() != null && product.getUnit() != null
                    && r.unit().factorTo(product.getUnit()) == null) {
                throw new BadRequestException(
                        "A unidade da receita (" + r.unit() + ") não é conversível pra unidade "
                                + "cadastrada no produto (" + product.getUnit() + ")");
            }

            SalonServiceProductUsage usage = new SalonServiceProductUsage();
            usage.setSalonService(service);
            usage.setProduct(product);
            usage.setQuantityUsed(r.quantityUsed());
            usage.setUnit(r.unit());
            return usage;
        }).collect(Collectors.toList());

        return serviceProductUsageRepository.saveAll(toSave).stream()
                .map(ServiceProductUsageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long id) {
        SalonService service = salonServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado"));
        service.setActive(false);
        salonServiceRepository.save(service);
    }

    /**
     * Exclusão definitiva (some do banco de vez) — diferente de {@link #delete}, que só
     * desativa e mantém o cadastro (reversível via {@link #reactivate}). Só permitida quando o
     * serviço nunca foi realizado em nenhum atendimento: apagar de vez corromperia esse
     * histórico. A própria receita do serviço (tb_salon_service_product_usage) cai sozinha via
     * ON DELETE CASCADE — não precisa de limpeza manual aqui.
     */
    @Transactional
    public void permanentlyDelete(Long id) {
        SalonService service = salonServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado"));

        if (appointmentServiceItemRepository.existsBySalonServiceId(id)) {
            throw new BusinessException(
                    "Não é possível excluir um serviço já realizado em algum atendimento — desative-o em vez de excluir.");
        }

        salonServiceRepository.delete(service);
    }

    @Transactional
    public SalonServiceResponse reactivate(Long id) {
        SalonService service = salonServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado"));
        service.setActive(true);
        return SalonServiceResponse.fromEntity(salonServiceRepository.save(service), loadUsages(id));
    }
}
