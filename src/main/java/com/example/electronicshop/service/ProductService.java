package com.example.electronicshop.service;

import com.example.electronicshop.config.CloudinaryConfig;
import com.example.electronicshop.config.Constant;
import com.example.electronicshop.map.ProductMapper;
import com.example.electronicshop.models.enity.Category;
import com.example.electronicshop.models.ResponseObject;
import com.example.electronicshop.models.product.Product;
import com.example.electronicshop.models.product.ProductImage;
import com.example.electronicshop.notification.AppException;
import com.example.electronicshop.notification.NotFoundException;
import com.example.electronicshop.communication.request.ProductReq;
import com.example.electronicshop.communication.response.ProductRes;
import com.example.electronicshop.repository.BrandRepository;
import com.example.electronicshop.repository.CategoryRepository;
import com.example.electronicshop.repository.ProductOptionRepository;
import com.example.electronicshop.repository.ProductRepository;
import com.mongodb.MongoWriteException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final CloudinaryConfig cloudinary;

    public ResponseEntity<?> findAll(boolean isAdmin, Pageable pageable) {
        Page<Product> products;
        if (isAdmin) products = productRepository.findAll(pageable);
        else products = productRepository.findAllByState(Constant.ENABLE, pageable);
        List<ProductRes> resList = products.getContent().stream().map(productMapper::toProductRes).collect(Collectors.toList());
        ResponseEntity<?> resp = addPageableToRes(products, resList);
        if (resp != null) return resp;
        throw new NotFoundException("Can not found any product");
    }

    private ResponseEntity<?> addPageableToRes(Page<Product> products, List<ProductRes> resList) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("list", resList);
        resp.put("totalQuantity", products.getTotalElements());
        resp.put("totalPage", products.getTotalPages());
        if (resList.size() >0 )
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Get all product success", resp));
        return null;
    }

    public ResponseEntity<?> findById(String id) {
        Optional<Product> product = productRepository.findProductByIdAndState(id, Constant.ENABLE);
        if (product.isPresent()) {
            ProductRes res = productMapper.toProductRes(product.get());
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Get product success", res));
        }
        throw new NotFoundException("Can not found any product with id: "+id);
    }

    public ResponseEntity<?> findByCategoryIdOrBrandId(String id, Pageable pageable) {
        Page<Product> products;
        try {
            Optional<Category> category = categoryRepository.findCategoryByIdAndState(id, Constant.ENABLE);
            if (category.isPresent()) {
                List<ObjectId> subCat = category.get().getSubCategories().stream().map(c -> new ObjectId(c.getId())).collect(Collectors.toList());
                products = productRepository.findProductsByCategory(new ObjectId(id),
                        subCat, pageable);
            } else products = productRepository.findAllByCategory_IdAndState(new ObjectId(id),
                    Constant.ENABLE, pageable);
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(), "Error when finding");
        }
        List<ProductRes> resList = products.stream().map(productMapper::toProductRes).collect(Collectors.toList());
        ResponseEntity<?> resp = addPageableToRes(products, resList);
        if (resp != null) return resp;
        throw new NotFoundException("Can not found any product with category or brand id: "+id);
    }

    public ResponseEntity<?> search(String key, Pageable pageable) {
        Page<Product> products;
        try {
            products = productRepository.findAllBy(TextCriteria
                            .forDefaultLanguage().matchingAny(key),
                    pageable);
        } catch (Exception e) {
            throw new NotFoundException("Can not found any product with: "+key);
        }
        List<ProductRes> resList = products.getContent().stream().map(productMapper::toProductRes).collect(Collectors.toList());
        ResponseEntity<?> resp = addPageableToRes(products, resList);
        if (resp != null) return resp;
        throw new NotFoundException("Can not found any product with: "+key);
    }

    public ResponseEntity<?> addProduct(ProductReq req) {
        if (req != null) {
            Product product = productMapper.toProduct(req);
            try {
                processUploadImage(req.getImages(),product);
                productRepository.save(product);
            } catch (Exception e) {
                throw new AppException(HttpStatus.CONFLICT.value(), "Product name already exists");
            }
            ProductRes res = productMapper.toProductRes(product);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new ResponseObject("true", "Add product successfully ", res)
            );
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ResponseObject("false", "Request is null", "")
        );
    }
    public List<ProductImage> processUploadImage (List<MultipartFile> images, Product product) {
        if (images == null || images.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST.value(), "images is empty");
        for (int i = 0; i < images.size(); i++) {
            try {
                String url = cloudinary.uploadImage(images.get(i), null);
                if (i == 0) product.getImages().add(new ProductImage(UUID.randomUUID().toString(), url));
                else product.getImages().add(new ProductImage(UUID.randomUUID().toString(), url));
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new AppException(HttpStatus.EXPECTATION_FAILED.value(), "Error when upload images");
            }
            productRepository.save(product);
        }
        return product.getImages();
    }
    @Transactional
    public ResponseEntity<?> updateProduct(String id, ProductReq req) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isPresent() && req != null) {
            processUpdate(req, product.get());
            try {
                productRepository.save(product.get());
            } catch (MongoWriteException e) {
                throw new AppException(HttpStatus.CONFLICT.value(), "Product name already exists");
            } catch (Exception e) {
                throw new AppException(HttpStatus.EXPECTATION_FAILED.value(), e.getMessage());
            }
            ProductRes res = productMapper.toProductRes(product.get());
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Update product successfully ", res)
            );
        }
        throw new NotFoundException("Can not found product with id: "+id);
    }

    public void processUpdate(ProductReq req, Product product) {
        if (!req.getName().equals(product.getName()))
            product.setName(req.getName());
        if (!req.getDescription().equals(product.getDescription()))
            product.setDescription(req.getDescription());
        if (!req.getPrice().equals(product.getPrice()))
            product.setPrice(req.getPrice());
        if (!req.getCategory().equals(product.getCategory().getId())) {
            Optional<Category> category = categoryRepository.findCategoryByIdAndState(req.getCategory(), Constant.ENABLE);
            if (category.isPresent())
                product.setCategory(category.get());
            else throw new NotFoundException("Can not found category with id: "+req.getCategory());
        }
//        if (!req.getBrand().equals(product.getBrand().getId())) {
//            Optional<Brand> brand = brandRepository.findBrandByIdAndState(req.getBrand(), Constant.ENABLE);
//            if (brand.isPresent())
//                product.setBrand(brand.get());
//            else throw new NotFoundException("Can not found brand with id: "+req.getBrand());
//        }
        if (req.getState() != null && !req.getState().isEmpty() &&
                (req.getState().equalsIgnoreCase(Constant.ENABLE) ||
                        req.getState().equalsIgnoreCase(Constant.DISABLE)))
            product.setState(req.getState());
        else throw new AppException(HttpStatus.BAD_REQUEST.value(), "Invalid state");
    }

    public ResponseEntity<?> deactivatedProduct(String id) {
        Optional<Product> product = productRepository.findProductByIdAndState(id, Constant.ENABLE);
        if (product.isPresent()) {
            product.get().setState(Constant.DISABLE);
            productRepository.save(product.get());
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Delete product successfully ", "")
            );
        } throw new NotFoundException("Can not found product with id: "+id);
    }

    @Transactional
    public ResponseEntity<?> destroyProduct(String id) {
        Optional<Product> product = productRepository.findProductByIdAndState(id, Constant.ENABLE);
        if (product.isPresent()) {
            try {
                productRepository.deleteById(product.get().getId());
                productOptionRepository.deleteByProduct_Id(product.get().getId());
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new NotFoundException("Error when destroy product with id: "+id);
            }
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Destroy product successfully ", "")
            );
        } throw new NotFoundException("Can not found product with id: "+id);
    }
}
