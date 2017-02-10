package org.rliz.mbs.artist.model;

import org.rliz.mbs.common.model.AbstractEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * Represents artist credit.
 */
@Entity
@Table(name = "artist_credit")
public class ArtistCredit extends AbstractEntity implements Serializable {


    @Column(name = "name")
    private String name;

    @Column(name = "artist_count")
    private Integer artistCount;

    @Column(name = "ref_count")
    private Integer refCount;

    @Column(name = "created")
    private Date created;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getArtistCount() {
        return artistCount;
    }

    public void setArtistCount(Integer artistCount) {
        this.artistCount = artistCount;
    }

    public Integer getRefCount() {
        return refCount;
    }

    public void setRefCount(Integer refCount) {
        this.refCount = refCount;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }
}
