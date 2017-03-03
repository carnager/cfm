package Cfm::Formatter;
use strict;
use warnings FATAL => 'all';
use Moo;
use Carp;

# Format a list of resources into a list of their names
sub name_list {
    my ($self, $objlist) = @_;

    my @name_list = map {
        $_->name
    } @$objlist;
    return join(", ", @name_list);
}

# Format a single artist
sub artist {
    carp "not implemented";
}

# Format a list of artists
sub artist_list {
    carp "not implemented";
}

# Format a single playback
sub playback {
    carp "not implemented";
}

# Format a list of playbacks
sub playback_list {
    carp "not implemented";
}

1;