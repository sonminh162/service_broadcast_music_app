package com.lifetime.push_music_app.models;

public class Song {

    public final String title;
    public final int trackNumber;
    public final int duration;
    public final String path;
    public final String albumName;
    public final int artistId;
    public final String artistName;
    private final int year;

    public Song(final String title, final int trackNumber, final int year, final int duration,
                final String path, final String albumName, final int artistId, final String artistName) {
        this.title = title;
        this.trackNumber = trackNumber;
        this.year = year;
        this.duration = duration;
        this.path = path;
        this.albumName = albumName;
        this.artistId = artistId;
        this.artistName = artistName;
    }

}
